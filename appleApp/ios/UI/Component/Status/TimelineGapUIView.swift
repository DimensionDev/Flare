import UIKit
import KotlinSharedUI

@MainActor
protocol TimelineGapActionObserver: AnyObject {
    func willLoadTimelineGap(_ gap: UiTimelineV2.Gap)
    func didLoadTimelineGap(_ gap: UiTimelineV2.Gap, insertedKeys: [String])
}

final class TimelineGapUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let button = UIButton(type: .system)
    private var gap: UiTimelineV2.Gap?

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(button)
        button.addTarget(self, action: #selector(loadGap), for: .touchUpInside)
        button.accessibilityIdentifier = "timeline-gap"
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(gap: UiTimelineV2.Gap) {
        self.gap = gap
        var configuration = UIButton.Configuration.plain()
        configuration.title = gap.loading ? String(localized: "timeline_loading_missing_posts") :
            gap.failed ? String(localized: "timeline_retry_missing_posts") : String(localized: "timeline_load_missing_posts")
        configuration.showsActivityIndicator = gap.loading
        button.configuration = configuration
        button.isEnabled = !gap.loading
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() { super.layoutSubviews(); button.frame = bounds }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: max(44, button.sizeThatFits(size).height))
    }
    override var intrinsicContentSize: CGSize { sizeThatFits(CGSize(width: bounds.width, height: .greatestFiniteMagnitude)) }
    func timelineHeight(for width: CGFloat) -> CGFloat? { sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height }

    @objc private func loadGap() {
        guard let gap, !gap.loading else { return }
        var responder: UIResponder? = self
        var observer: TimelineGapActionObserver?
        while let current = responder {
            if let target = current as? TimelineGapActionObserver { observer = target; break }
            responder = current.next
        }
        observer?.willLoadTimelineGap(gap)
        gap.onActivate? { [weak observer] keys in
            DispatchQueue.main.async { observer?.didLoadTimelineGap(gap, insertedKeys: keys) }
            return KotlinUnit.shared
        }
    }
}
