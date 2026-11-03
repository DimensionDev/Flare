#if DEBUG
import FlareAppleUI
import KotlinSharedUI
import SwiftUI
import UIKit

/// Exercise the real timeline under the same collapsing bars as Home.
struct TimelineMediaScrollTestFixture: View {
    var body: some View {
        if #available(iOS 26.0, *) {
            TabView {
                Tab("Home", systemImage: "house") {
                    NavigationStack {
                        TimelineMediaScrollTestView()
                            .ignoresSafeArea(edges: .vertical)
                            .navigationTitle("Timeline")
                            .navigationBarTitleDisplayMode(.inline)
                            .modifier(ScrollMinimizingNavigationBar())
                    }
                }
                Tab("Other", systemImage: "person") { Text("Other") }
            }
            .tabBarMinimizeBehavior(.onScrollDown)
        } else {
            TimelineMediaScrollTestView()
        }
    }
}

private struct TimelineMediaScrollTestView: UIViewControllerRepresentable {
    func makeCoordinator() -> TimelineScrollMeasurements { TimelineScrollMeasurements() }

    func makeUIViewController(context: Context) -> UITimelineCollectionViewController {
        context.coordinator.controller
    }

    func updateUIViewController(_ controller: UITimelineCollectionViewController, context: Context) {}

    static func dismantleUIViewController(_ controller: UITimelineCollectionViewController, coordinator: TimelineScrollMeasurements) {
        coordinator.displayLink?.invalidate()
    }
}

private final class TimelineScrollMeasurements: NSObject {
    let controller = UITimelineCollectionViewController(detailStatusKey: nil)
    var displayLink: CADisplayLink?
    private var list: TimelineCollectionView {
        controller.view.subviews.compactMap { $0 as? TimelineCollectionView }.first!
    }
    private let label = UILabel()
    private var previous: (offset: CGFloat, pan: CGFloat, frames: [String: CGRect])?
    private var samples = 0
    private var maxError: CGFloat = 0
    private var maxHeightChange: CGFloat = 0
    private var minTopInset = CGFloat.greatestFiniteMagnitude
    private var maxTopInset: CGFloat = 0

    override init() {
        super.init()
        let helper = TimelinePresentationAppearancePatchHelper.shared
        controller.appearance = TimelineUIKitAppearance(timeline: helper.resolve(
            base: TimelineAppearance.companion.Default,
            patch: helper.setLimitSingleMediaHeight(patch: helper.empty, value: true)))
        var input = TimelineContent()
        input.state = .loaded
        input.items = (0..<100).map {
            .post(makeTimelineTestRow($0, imageHeight: 1_600, mediaCount: 1, textRepeatCount: $0 % 4))
        }
        controller.submit(input, columns: 1)
        controller.loadViewIfNeeded()

        let reset = UIButton(type: .system)
        reset.setTitle("Reset measurements", for: .normal)
        reset.accessibilityIdentifier = "timeline-scroll-reset"
        reset.addAction(UIAction { [weak self] _ in self?.reset() }, for: .touchUpInside)
        reset.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(reset)
        label.accessibilityIdentifier = "timeline-scroll-metrics"
        label.isAccessibilityElement = true
        label.font = .systemFont(ofSize: 8)
        label.numberOfLines = 2
        label.backgroundColor = .systemBackground
        label.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(label)
        NSLayoutConstraint.activate([
            reset.topAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.topAnchor),
            reset.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor, constant: -8),
            label.topAnchor.constraint(equalTo: reset.bottomAnchor),
            label.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor),
            label.heightAnchor.constraint(equalToConstant: 28)
        ])
        displayLink = CADisplayLink(target: self, selector: #selector(sample))
        displayLink?.add(to: .main, forMode: .common)
    }

    private func reset() {
        previous = nil
        samples = 0
        maxError = 0
        maxHeightChange = 0
        minTopInset = .greatestFiniteMagnitude
        maxTopInset = 0
        publish()
    }

    @objc private func sample() {
        defer { publish() }
        guard list.isDragging else { previous = nil; return }
        minTopInset = min(minTopInset, list.safeAreaInsets.top)
        maxTopInset = max(maxTopInset, list.safeAreaInsets.top)
        let offset = list.contentOffset.y
        let pan = list.panGestureRecognizer.translation(in: controller.view).y
        let frames = Dictionary(uniqueKeysWithValues: list.visibleCells.compactMap { cell -> (String, CGRect)? in
            guard let path = list.indexPath(for: cell), let id = list.readingItemID?(path) else { return nil }
            return (id, cell.frame)
        })
        defer { previous = (offset, pan, frames) }
        // Avoid elastic edges and the translation reset at the start of each gesture.
        guard let old = previous, offset > 30, old.offset > 30 else { return }
        for (id, frame) in frames {
            guard let oldFrame = old.frames[id] else { continue }
            let movement = frame.minY - offset - (oldFrame.minY - old.offset)
            maxError = max(maxError, abs(movement - (pan - old.pan)))
            maxHeightChange = max(maxHeightChange, abs(frame.height - oldFrame.height))
            samples += 1
        }
    }

    private func publish() {
        let media = list.visibleCells.lazy.flatMap(descendants).compactMap { $0 as? StatusMediaUIView }.first
        let values: [String: CGFloat] = [
            "samples": CGFloat(samples), "maxError": maxError, "maxHeightChange": maxHeightChange,
            "topInsetRange": max(maxTopInset - minTopInset, 0), "mediaHeight": media?.bounds.height ?? 0
        ]
        let data = try! JSONSerialization.data(withJSONObject: values, options: .sortedKeys)
        label.text = String(decoding: data, as: UTF8.self)
    }

    private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
}
#endif
