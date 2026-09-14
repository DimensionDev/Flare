import Foundation
import QuartzCore
import FlareLazyBenchmark
#if os(macOS)
import AppKit
typealias BenchmarkView = NSView
#else
import UIKit
typealias BenchmarkView = UIView
#endif

struct Scenario {
    let name: String
    let count: Int
    let cards: Bool
    let horizontal: Bool

    init(_ name: String) {
        self.name = name
        count = Int(name.split(separator: "_").last!)!
        cards = name.hasPrefix("cards")
        horizontal = name.hasPrefix("row")
    }

    func extent(_ key: Int) -> CGFloat {
        let remainder = { (value: Int, divisor: Int) in (value % divisor + divisor) % divisor }
        return CGFloat(cards ? 156 + remainder(key, 3) * 24 : 44 + remainder(key, 5) * 12)
    }
}

struct VisibleItem: Equatable {
    let index: Int
    let key: Int
    let offset: CGFloat
    let extent: CGFloat
}

@MainActor final class NativeModel {
    let scenario: Scenario
    var prefix = 0
    var expandedKey: Int?
    var expanded = false
    var count: Int { scenario.count + prefix }
    init(_ scenario: Scenario) { self.scenario = scenario }
    func key(_ index: Int) -> Int { index - prefix }
    func extent(_ index: Int) -> CGFloat {
        scenario.extent(key(index)) + (expanded && key(index) == expandedKey ? 24 : 0)
    }
    func strings(_ index: Int) -> [String] {
        let key = key(index)
        return scenario.cards ? [
            "Author \(key)", "12:34",
            "Post \(key): variable-size native lazy content with a longer body, nested stacks, stable identity, and saveable item state.",
            "Reply 12", "Repost 34", "Like 56"
        ] : ["Item \(key)"]
    }
}

@MainActor protocol BenchmarkDriver: AnyObject {
    var view: BenchmarkView { get }
    var created: Int { get }
    var active: Int { get }
    var offset: CGFloat { get }
    func mount()
    func layout()
    func items() -> [VisibleItem]
    func ready() -> Bool
    func scroll(_ offset: CGFloat)
    func physicalScroll(_ active: Bool)
    func jump(_ index: Int)
    func prepend()
    func resize()
    func dispose()
}

@MainActor final class FlareDriver: BenchmarkDriver {
    private let session: AppleLazyBenchmarkSession
    var view: BenchmarkView { session.view as! BenchmarkView }
    var created: Int { Int(session.created) }
    var active: Int { Int(session.active) }
    var offset: CGFloat { session.offset() }
    init(_ scenario: Scenario) {
        session = AppleLazyBenchmarkSession(count: Int32(scenario.count), cards: scenario.cards, horizontal: scenario.horizontal)
    }
    func mount() { session.mount() }
    func layout() { session.layout() }
    func ready() -> Bool { session.ready() }
    func items() -> [VisibleItem] {
        session.items().map { VisibleItem(index: Int($0.index), key: Int($0.key), offset: $0.offset, extent: $0.extent) }
    }
    func scroll(_ offset: CGFloat) { session.scroll(offset: offset) }
    func physicalScroll(_ active: Bool) { session.physicalScroll(active: active) }
    func jump(_ index: Int) { session.jump(index: Int32(index)) }
    func prepend() { session.prepend() }
    func resize() { session.resize() }
    func dispose() { session.dispose() }
}

enum BenchmarkFailure: Error { case invalid(String) }

@MainActor final class BenchmarkRunner {
    private let container: BenchmarkView
    private let scenario: Scenario
    private let backend: String
    private var samples: [String] = []
    private let header = "backend,scenario,operation,iteration,nanoseconds,created,active"

    init(container: BenchmarkView) {
        self.container = container
        let args = ProcessInfo.processInfo.arguments
        func option(_ name: String, _ fallback: String) -> String {
            guard let index = args.firstIndex(of: name), args.indices.contains(index + 1) else { return fallback }
            return args[index + 1]
        }
        scenario = Scenario(option("--scenario", "column_10000"))
        backend = option("--backend", "flare")
    }

    private func makeDriver() -> BenchmarkDriver {
        backend == "flare" ? FlareDriver(scenario) : NativeDriver(scenario)
    }

    private func attach(_ driver: BenchmarkDriver) {
        driver.view.frame = container.bounds
        container.addSubview(driver.view)
        driver.mount()
    }

    private func nextTurn() async {
        await withCheckedContinuation { continuation in
            DispatchQueue.main.async { continuation.resume() }
        }
    }

    private func settle(_ driver: BenchmarkDriver) async throws {
        let start = DispatchTime.now().uptimeNanoseconds
        var previous: [VisibleItem] = []
        var stable = 0
        while stable < 2 {
            driver.layout()
            await nextTurn()
            driver.layout()
            CATransaction.flush()
            let current = driver.items()
            stable = driver.ready() && !current.isEmpty && current == previous ? stable + 1 : 0
            previous = current
            if DispatchTime.now().uptimeNanoseconds - start > 5_000_000_000 {
                throw BenchmarkFailure.invalid("Layout did not settle: \(current)")
            }
        }
    }

    private func require(_ condition: Bool, _ message: String) throws {
        if !condition { throw BenchmarkFailure.invalid(message) }
    }

    private func emit(_ operation: String, _ iteration: Int, _ nanos: UInt64, _ created: Int, _ active: Int) {
        let row = "\(backend),\(scenario.name),\(operation),\(iteration),\(nanos),\(created),\(active)"
        samples.append(row)
        print("APPLE_LAZY_SAMPLE,\(row)")
    }

    private func measure(_ driver: BenchmarkDriver, _ operation: String, _ warmup: Int, _ count: Int,
                         action: (Int) -> Void, verify: () throws -> Void = {}) async throws {
        for iteration in 0..<(warmup + count) {
            let created = driver.created
            let start = DispatchTime.now().uptimeNanoseconds
            action(iteration)
            try await settle(driver)
            let nanos = DispatchTime.now().uptimeNanoseconds - start
            try verify()
            if iteration >= warmup { emit(operation, iteration - warmup, nanos, driver.created - created, driver.active) }
        }
    }

    func run() async {
        var exitCode: Int32 = 0
        do {
            print("APPLE_LAZY_BEGIN,\(backend),\(scenario.name),\(ProcessInfo.processInfo.operatingSystemVersionString)")
            try require(container.bounds.size == CGSize(width: 390, height: 780), "Viewport must be 390 by 780 pt")
            for iteration in 0..<25 {
                let start = DispatchTime.now().uptimeNanoseconds
                let driver = makeDriver()
                attach(driver)
                try await settle(driver)
                let nanos = DispatchTime.now().uptimeNanoseconds - start
                try require(driver.items().first?.index == 0, "Mount lost first item")
                if iteration >= 5 { emit("mount", iteration - 5, nanos, driver.created, driver.active) }
                driver.dispose()
                driver.view.removeFromSuperview()
                await nextTurn()
            }
            let driver = makeDriver()
            attach(driver)
            defer { driver.dispose(); driver.view.removeFromSuperview() }
            try await settle(driver)
            try await measure(driver, "idle", 5, 20, action: { _ in })
            driver.physicalScroll(true)
            var requested: CGFloat = 0
            try await measure(driver, "scroll_48pt", 30, 180, action: { _ in
                requested = driver.offset + 48
                driver.scroll(requested)
            }, verify: {
                try self.require(abs(driver.offset - requested) < 1, "Physical offset did not advance")
            })
            driver.physicalScroll(false)
            try await settle(driver)
            var target = 0
            try await measure(driver, "jump", 5, 25, action: { iteration in
                target = self.scenario.count / 10 + (iteration * 7919) % (self.scenario.count * 8 / 10)
                driver.jump(target)
            }, verify: {
                try self.require(driver.items().contains { $0.index == target && abs($0.offset + 13) < 1 }, "Jump lost target")
            })
            driver.jump(scenario.count * 3 / 4)
            try await settle(driver)
            var anchor = driver.items().first!
            try await measure(driver, "prepend_20", 3, 20, action: { _ in
                anchor = driver.items().first!
                driver.prepend()
            }, verify: {
                let first = driver.items().first!
                try self.require(first.key == anchor.key && first.index == anchor.index + 20 && abs(first.offset - anchor.offset) < 1, "Insertion did not shift and preserve the anchor")
            })
            let resizeAnchor = driver.items().first!
            let resizedKey = resizeAnchor.key
            var expanded = false
            try await measure(driver, "resize_visible", 3, 20, action: { _ in
                expanded.toggle()
                driver.resize()
            }, verify: {
                let expected = self.scenario.extent(resizedKey) + (expanded ? 24 : 0)
                try self.require(driver.items().contains { $0.key == resizedKey && abs($0.extent - expected) < 1 }, "Resize did not reach native view")
                try self.require(driver.items().first?.key == resizedKey && abs(driver.items().first!.offset - resizeAnchor.offset) < 1, "Resize lost anchor")
            })
            let output = ([header] + samples).joined(separator: "\n") + "\n"
            #if os(macOS)
            let directory = FileManager.default.temporaryDirectory
            #else
            let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            #endif
            let file = directory.appendingPathComponent("apple-lazy-\(backend)-\(scenario.name).csv")
            try output.write(to: file, atomically: true, encoding: .utf8)
            print("APPLE_LAZY_COMPLETED,\(backend),\(scenario.name),\(file.path)")
        } catch {
            print("APPLE_LAZY_FAILED,\(backend),\(scenario.name),\(error)")
            exitCode = 1
        }
        fflush(stdout)
        exit(exitCode)
    }
}
