import UIKit
import SwiftUI
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI

final class ProfileMediaCollectionViewCell: UICollectionViewCell {
    private let mediaView = StatusMediaContentUIView()
    private let placeholderView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
        contentView.clipsToBounds = true

        mediaView.translatesAutoresizingMaskIntoConstraints = false
        placeholderView.translatesAutoresizingMaskIntoConstraints = false
        placeholderView.backgroundColor = .tertiarySystemFill
        placeholderView.layer.cornerRadius = 12
        placeholderView.clipsToBounds = true
        contentView.addSubview(mediaView)
        contentView.addSubview(placeholderView)
        NSLayoutConstraint.activate([
            mediaView.topAnchor.constraint(equalTo: contentView.topAnchor),
            mediaView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            mediaView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            mediaView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            placeholderView.topAnchor.constraint(equalTo: contentView.topAnchor),
            placeholderView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            placeholderView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            placeholderView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        configurePlaceholder()
    }

    func configure(item: ProfileMedia, appearance: StatusUIKitAppearance, onTap: @escaping () -> Void) {
        placeholderView.isHidden = true
        mediaView.isHidden = false
        mediaView.configure(
            data: [item.media],
            sensitive: item.status.timelineContentPost?.sensitive ?? false,
            cornerRadius: 12,
            appearanceShowMedia: true,
            appearanceShowSensitive: appearance.showSensitiveContent,
            appearanceExpandMediaSize: true,
            appearanceLimitMediaGridToNine: true,
            appearanceMediaLayout: .grid,
            carouselLeadingPadding: 0,
            carouselTrailingPadding: 0
        )
        mediaView.onMediaClicked = { _, _ in onTap() }
    }

    func configurePlaceholder() {
        mediaView.onMediaClicked = nil
        mediaView.prepareForPoolRemoval()
        mediaView.isHidden = true
        placeholderView.isHidden = false
    }
}

final class TimelineUIKitCollectionViewCell: UICollectionViewCell {
    var onPreferredHeightChanged: ((CGFloat, CGFloat) -> Void)?
    var cachedPreferredHeight: ((CGFloat) -> CGFloat?)?

    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private var timelineViewStorage: TimelineUIView?
    private var timelineCardStorage: AdaptiveTimelineCardUIView?
    private var placeholderCardStorage: AdaptiveTimelineCardUIView?

    // Rebuild-skip signature. When the incoming data + appearance + detail-key are
    // identical to the previous configure we short-circuit the expensive
    // `TimelineUIView.configure` → `StatusUIKitView.rebuild()` path.
    private var lastRenderHash: Int32?
    private var lastItemKey: String?
    private var lastAppearance: TimelineUIKitAppearance?
    private var lastDetailStatusKey: String?
    private var lastAiTldrEnabled: Bool?
    private var pendingFreshMeasurement = false
    private var lastMeasuredWidth: CGFloat?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        // Reset signature so a recycled cell always rebuilds for its new tenant,
        // even in the (unlikely) event that renderHash/itemKey collide.
        resetRenderSignature()
        onPreferredHeightChanged = nil
        cachedPreferredHeight = nil
        pendingFreshMeasurement = false
        lastMeasuredWidth = nil
    }

    func autoplayCandidates(prefix: String) -> [TimelineVideoAutoplayCandidate] {
        guard hostedView === timelineCardStorage else {
            return []
        }
        return timelineViewStorage?.autoplayCandidates(prefix: prefix) ?? []
    }

    func performDeferredPoolCleanup() {
        guard let timelineView = timelineViewStorage else { return }
        if window == nil || hostedView !== timelineCardStorage {
            resetRenderSignature()
            timelineView.prepareForDeferredReuseCleanup()
        } else {
            timelineView.performDeferredPoolCleanup()
        }
    }

    func performLightweightPoolCleanup() {
        guard window != nil,
              hostedView === timelineCardStorage,
              let timelineView = timelineViewStorage else { return }
        timelineView.performLightweightPoolCleanup()
    }

    private func resetRenderSignature() {
        lastRenderHash = nil
        lastItemKey = nil
        lastAppearance = nil
        lastDetailStatusKey = nil
        lastAiTldrEnabled = nil
    }

    func configureTimeline(
        data: UiTimelineV2,
        index: Int,
        totalCount: Int,
        appearance: TimelineUIKitAppearance,
        detailStatusKey: MicroBlogKey?,
        aiTldrEnabled: Bool,
        isMultipleColumn: Bool,
        openURL: ((URL) -> Void)?
    ) {
        let timelineView = resolvedTimelineView()
        let timelineCard = resolvedTimelineCard()
        timelineView.onLocalHeightInvalidated = { [weak self] in
            self?.handleLocalTimelineHeightInvalidated()
        }
        // Card styling is cheap; always reapply so index/totalCount changes
        // (affecting the card's outer rounded corners) are picked up.
        if timelineCard.isMultipleColumn != isMultipleColumn {
            pendingFreshMeasurement = true
        }
        timelineCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        timelineCard.isMultipleColumn = isMultipleColumn
        timelineCard.configure(index: index, totalCount: totalCount)

        let itemKey = data.itemKey ?? ""
        let detailKeyStr = detailStatusKey.map { String(describing: $0) } ?? ""
        let dataUnchanged =
            lastRenderHash == data.renderHash &&
            lastItemKey == itemKey &&
            lastAppearance == appearance &&
            lastDetailStatusKey == detailKeyStr &&
            lastAiTldrEnabled == aiTldrEnabled

        if !dataUnchanged {
            pendingFreshMeasurement = true
            lastRenderHash = data.renderHash
            lastItemKey = itemKey
            lastAppearance = appearance
            lastDetailStatusKey = detailKeyStr
            lastAiTldrEnabled = aiTldrEnabled
            timelineView.configure(
                data: data,
                appearance: appearance.status,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                onOpenURL: openURL
            )
        } else {
            // Same render state — just refresh the click callback in case the
            // parent routed a new openURL handler through.
            timelineView.onOpenURL = openURL
        }
        setHostedView(timelineCard)
    }

    func configurePlaceholder(
        index: Int,
        totalCount: Int,
        appearance: TimelineUIKitAppearance,
        isMultipleColumn: Bool
    ) {
        let placeholderCard = resolvedPlaceholderCard()
        placeholderCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        placeholderCard.isMultipleColumn = isMultipleColumn
        placeholderCard.configure(index: index, totalCount: totalCount)
        setHostedView(placeholderCard)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
        reportPreferredHeightIfNeeded()
    }

    override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        attributes
    }

    func setHostedView(_ view: UIView?) {
        contentConfiguration = nil
        backgroundConfiguration = .clear()
        if hostedView === view {
            view?.invalidateIntrinsicContentSize()
            view?.setNeedsLayout()
            contentView.setNeedsLayout()
            setNeedsLayout()
            return
        }
        NSLayoutConstraint.deactivate(hostedConstraints)
        hostedConstraints = []
        hostedBottomConstraint = nil
        hostedView?.removeFromSuperview()
        hostedView = view

        guard let view else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(view)
        let bottomConstraint = view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bottomConstraint.priority = .init(999)
        hostedConstraints = [
            view.topAnchor.constraint(equalTo: contentView.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottomConstraint,
        ]
        hostedBottomConstraint = bottomConstraint
        NSLayoutConstraint.activate(hostedConstraints)
    }

    func hasMeasuredHeight(for width: CGFloat) -> Bool {
        !pendingFreshMeasurement && lastMeasuredWidth == width
    }

    private func measuredHostedHeight(width: CGFloat) -> CGFloat {
        guard let hostedView else { return 0 }

        if lastMeasuredWidth != width {
            lastMeasuredWidth = width
            pendingFreshMeasurement = true
        }
        if !pendingFreshMeasurement,
           hostedView === timelineCardStorage,
           let cachedHeight = cachedPreferredHeight?(width),
           cachedHeight > 0,
           cachedHeight.isFinite {
            return cachedHeight
        }

        if hostedView === timelineCardStorage {
            let cardWrapperWidth: CGFloat = timelineCardStorage?.isMultipleColumn == true ? 4 : 0
            timelineViewStorage?.prepareForFitting(width: max(width - cardWrapperWidth - 32, 1))
        }

        contentView.bounds = CGRect(x: 0, y: 0, width: width, height: contentView.bounds.height)
        hostedView.bounds = CGRect(x: 0, y: 0, width: width, height: hostedView.bounds.height)
        hostedView.setNeedsLayout()

        let height = childHeight(of: hostedView, for: width)
        let preferredHeight = max(ceil(height) + 1, 1)
        if hostedView === timelineCardStorage {
            pendingFreshMeasurement = false
            onPreferredHeightChanged?(width, preferredHeight)
        }
        return preferredHeight
    }

    private func reportPreferredHeightIfNeeded() {
        guard hostedView === timelineCardStorage, contentView.bounds.width > 1 else { return }
        // A fresh measurement reports once, even if the height did not change:
        // the controller may be waiting for the new width/render to be measured.
        _ = measuredHostedHeight(width: contentView.bounds.width)
    }

    private func handleLocalTimelineHeightInvalidated() {
        // Cache is keyed by item+width only; we must skip the cached lookup once
        // so the next measurement reflects the new local UI state (expanded
        // content warning, show-more, summary) before refreshing the cache.
        pendingFreshMeasurement = true
        contentView.invalidateIntrinsicContentSize()
        contentView.setNeedsLayout()
        setNeedsLayout()
    }

    private func resolvedTimelineView() -> TimelineUIView {
        if let timelineViewStorage {
            return timelineViewStorage
        }
        let view = TimelineUIView()
        timelineViewStorage = view
        return view
    }

    private func resolvedTimelineCard() -> AdaptiveTimelineCardUIView {
        if let timelineCardStorage {
            return timelineCardStorage
        }
        let card = AdaptiveTimelineCardUIView()
        card.setContent(UIView.padding(resolvedTimelineView(), insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)))
        timelineCardStorage = card
        return card
    }

    private func resolvedPlaceholderCard() -> AdaptiveTimelineCardUIView {
        if let placeholderCardStorage {
            return placeholderCardStorage
        }
        let card = makeTimelinePlaceholderCardUIView()
        placeholderCardStorage = card
        return card
    }
}

final class TimelinePlaceholderCollectionViewCell: UICollectionViewCell {
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private var placeholderCardStorage: AdaptiveTimelineCardUIView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configurePlaceholder(index: Int, totalCount: Int, appearance: TimelineUIKitAppearance, isMultipleColumn: Bool) {
        let placeholderCard = resolvedPlaceholderCard()
        placeholderCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        placeholderCard.isMultipleColumn = isMultipleColumn
        placeholderCard.configure(index: index, totalCount: totalCount)
        setHostedView(placeholderCard)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
    }

    override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        attributes
    }

    private func setHostedView(_ view: UIView?) {
        contentConfiguration = nil
        backgroundConfiguration = .clear()
        if hostedView === view {
            view?.invalidateIntrinsicContentSize()
            view?.setNeedsLayout()
            contentView.setNeedsLayout()
            setNeedsLayout()
            return
        }
        NSLayoutConstraint.deactivate(hostedConstraints)
        hostedConstraints = []
        hostedBottomConstraint = nil
        hostedView?.removeFromSuperview()
        hostedView = view

        guard let view else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(view)
        let bottomConstraint = view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bottomConstraint.priority = .init(999)
        hostedConstraints = [
            view.topAnchor.constraint(equalTo: contentView.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottomConstraint,
        ]
        hostedBottomConstraint = bottomConstraint
        NSLayoutConstraint.activate(hostedConstraints)
    }

    private func resolvedPlaceholderCard() -> AdaptiveTimelineCardUIView {
        if let placeholderCardStorage {
            return placeholderCardStorage
        }
        let card = makeTimelinePlaceholderCardUIView()
        placeholderCardStorage = card
        return card
    }
}

final class TimelineHostedViewCell: UICollectionViewCell {
    var onPreferredHeightChanged: ((CGFloat, CGFloat) -> Void)?
    private var measuredWidth: CGFloat?
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        onPreferredHeightChanged = nil
        setHostedView(nil)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
        if let onPreferredHeightChanged, let hostedView,
           contentView.bounds.width > 1, measuredWidth != contentView.bounds.width {
            let width = contentView.bounds.width
            measuredWidth = width
            let size = hostedView.systemLayoutSizeFitting(
                CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            )
            onPreferredHeightChanged(width, max(ceil(size.height) + 1, 1))
        }
    }

    override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        attributes
    }

    func setHostedView(_ view: UIView?) {
        measuredWidth = nil
        contentConfiguration = nil
        backgroundConfiguration = .clear()
        if hostedView === view {
            view?.invalidateIntrinsicContentSize()
            view?.setNeedsLayout()
            contentView.setNeedsLayout()
            setNeedsLayout()
            return
        }
        NSLayoutConstraint.deactivate(hostedConstraints)
        hostedConstraints = []
        hostedBottomConstraint = nil
        // The view may already have moved to another reusable cell. Only the
        // cell that still owns it should detach it during reuse.
        if hostedView?.superview === contentView {
            hostedView?.removeFromSuperview()
        }
        hostedView = view

        guard let view else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(view)
        let bottomConstraint = view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bottomConstraint.priority = .init(999)
        hostedConstraints = [
            view.topAnchor.constraint(equalTo: contentView.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottomConstraint,
        ]
        hostedBottomConstraint = bottomConstraint
        NSLayoutConstraint.activate(hostedConstraints)
    }
}

final class CenteredCellContentView: UIView {
    init(content: UIView) {
        super.init(frame: .zero)
        content.translatesAutoresizingMaskIntoConstraints = false
        addSubview(content)
        NSLayoutConstraint.activate([
            content.centerXAnchor.constraint(equalTo: centerXAnchor),
            content.centerYAnchor.constraint(equalTo: centerYAnchor),
            content.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 16),
            content.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -16),
            content.topAnchor.constraint(greaterThanOrEqualTo: topAnchor, constant: 16),
            content.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor, constant: -16),
            heightAnchor.constraint(greaterThanOrEqualToConstant: 160),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }
}
