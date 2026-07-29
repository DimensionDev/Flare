@preconcurrency import FlareUI
import UIKit
import XCTest

final class NativeVsFlareBenchmarkTests: XCTestCase {
    @MainActor
    func testMount100LabelsNativeUIKit() {
        measure(
            metrics: [XCTClockMetric()],
            options: manualMeasurementOptions()
        ) {
            var roots: [UIStackView] = []
            roots.reserveCapacity(Self.mountBatchSize)

            startMeasuring()
            for _ in 0..<Self.mountBatchSize {
                let root = makeNativeTextTree()
                layout(root)
                roots.append(root)
            }
            stopMeasuring()

            XCTAssertEqual(
                roots.last?.arrangedSubviews
                    .first
                    .flatMap { $0 as? UIStackView }?
                    .arrangedSubviews
                    .count,
                Self.itemCount
            )
        }
    }

    @MainActor
    func testMount100LabelsFlareUIWarmRuntime() {
        let runtime = FlareAppleBenchmarkRuntime()
        defer { runtime.dispose() }

        measure(
            metrics: [XCTClockMetric()],
            options: manualMeasurementOptions()
        ) {
            var hosts: [FlareUIKitBenchmarkHost] = []
            hosts.reserveCapacity(Self.mountBatchSize)

            startMeasuring()
            for _ in 0..<Self.mountBatchSize {
                let host = FlareUIKitBenchmarkHost(runtime: runtime)
                layout(host.view)
                hosts.append(host)
            }
            stopMeasuring()

            XCTAssertEqual(hosts.last?.renderedText(), Self.initialText)
            hosts.forEach { $0.dispose() }
        }
    }

    @MainActor
    func testMount100LabelsFlareUIColdRuntime() {
        measure(
            metrics: [XCTClockMetric()],
            options: manualMeasurementOptions()
        ) {
            startMeasuring()
            let runtime = FlareAppleBenchmarkRuntime()
            let host = FlareUIKitBenchmarkHost(runtime: runtime)
            layout(host.view)
            stopMeasuring()

            XCTAssertEqual(host.renderedText(), Self.initialText)
            host.dispose()
            runtime.dispose()
        }
    }

    @MainActor
    func testUpdateOneOf100LabelsNativeUIKit() {
        let root = makeNativeTextTree()
        layout(root)
        let column = root.arrangedSubviews[0] as! UIStackView
        let target = column.arrangedSubviews[Self.updatedIndex] as! UILabel
        var nextText = Self.updatedText

        measure(
            metrics: [XCTClockMetric()],
            options: automaticMeasurementOptions()
        ) {
            for _ in 0..<Self.updateBatchSize {
                target.text = nextText
                nextText =
                    nextText == Self.updatedText
                        ? Self.initialText
                        : Self.updatedText
                layout(root)
            }
        }

        XCTAssertEqual(target.text, Self.initialText)
    }

    @MainActor
    func testUpdateOneOf100LabelsFlareUI() {
        let runtime = FlareAppleBenchmarkRuntime()
        let host = FlareUIKitBenchmarkHost(runtime: runtime)
        layout(host.view)
        var nextText = Self.updatedText

        measure(
            metrics: [XCTClockMetric()],
            options: automaticMeasurementOptions()
        ) {
            for _ in 0..<Self.updateBatchSize {
                host.updateText(value: nextText)
                nextText =
                    nextText == Self.updatedText
                        ? Self.initialText
                        : Self.updatedText
                layout(host.view)
            }
        }

        XCTAssertEqual(host.renderedText(), Self.initialText)
        host.dispose()
        runtime.dispose()
    }

    @MainActor
    private func makeNativeTextTree() -> UIStackView {
        let root = UIStackView()
        let column = UIStackView()
        column.axis = .vertical
        for text in Self.initialItemTexts {
            let label = UILabel()
            label.text = text
            column.addArrangedSubview(label)
        }
        root.addArrangedSubview(column)
        return root
    }

    @MainActor
    private func layout(_ view: UIView) {
        view.frame = Self.layoutFrame
        view.setNeedsLayout()
        view.layoutIfNeeded()
    }

    @MainActor
    private func manualMeasurementOptions() -> XCTMeasureOptions {
        let options = automaticMeasurementOptions()
        options.invocationOptions = [.manuallyStart, .manuallyStop]
        return options
    }

    @MainActor
    private func automaticMeasurementOptions() -> XCTMeasureOptions {
        let options = XCTMeasureOptions()
        options.iterationCount = Self.measurementIterations
        return options
    }

    private static let itemCount = 100
    private static let updatedIndex = itemCount / 2
    private static let initialText = "Item 50 A"
    private static let updatedText = "Item 50 B"
    private static let initialItemTexts =
        (0..<itemCount).map { index in
            index == updatedIndex ? initialText : "Item \(index)"
        }
    private static let mountBatchSize = 10
    private static let updateBatchSize = 20
    private static let measurementIterations = 10
    private static let layoutFrame = CGRect(x: 0, y: 0, width: 390, height: 10_000)
}
