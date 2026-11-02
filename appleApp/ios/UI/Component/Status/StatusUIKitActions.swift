import UIKit
import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

// MARK: - StatusActionsUIView
// Mirrors StatusActionsView/StatusActionView/StatusActionItemView in
// StatusActionView.swift — horizontal bar of action buttons with optional
// group (menu) children, optional text labels, Spacer insertion per
// postActionStyle, haptic feedback on tap.
final class StatusActionsUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onOpenURL: ((URL) -> Void)?

    var hasVisibleActions: Bool { !managedChildren.isEmpty }

    private var managedChildren: [UIView] = []
    private var childSizes: [CGSize] = []
    private var contentHeight: CGFloat = 0
    private var layoutFrames: [CGRect] = []
    private var layoutWidth: CGFloat?
    private var layoutRightToLeft = false

    // Inputs
    private var data: [ActionMenu] = []
    private var postKey: String?
    private var useText: Bool = false
    private var allowSpacer: Bool = true
    private var postActionStyle: PostActionStyle = .leftAligned
    private var postActionLayout: PostActionLayoutConfig = PostActionLayoutConfig.companion.Default
    private var postActionFixedWidth: Bool = true
    private var showNumbers: Bool = true
    private var metrics = StatusActionLayoutMetrics(fontSize: 13, scale: UIScreen.main.scale)
    private var textStyle: UIFont.TextStyle = .footnote
    private var itemButtonPool: [ActionItemControl] = []
    private var groupButtonPool: [ActionItemControl] = []
    private var textGroupPool: [ActionGroupColumnView] = []
    private var dividerPool: [UIView] = []
    private var itemButtonCursor = 0
    private var groupButtonCursor = 0
    private var textGroupCursor = 0
    private var dividerCursor = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(
        data: [ActionMenu],
        useText: Bool,
        allowSpacer: Bool = true,
        postActionStyle: PostActionStyle,
        postActionLayout: PostActionLayoutConfig = PostActionLayoutConfig.companion.Default,
        postActionFixedWidth: Bool = true,
        applyPostActionLayout: Bool = true,
        showNumbers: Bool,
        isDetail: Bool,
        postKey: String? = nil
    ) {
        self.data = applyPostActionLayout
            ? castActionMenus(PostActionLayoutHelpers.shared.apply(actions: data, config: postActionLayout))
            : data
        self.useText = useText
        self.postKey = postKey
        self.allowSpacer = allowSpacer
        self.postActionStyle = postActionStyle
        self.postActionLayout = postActionLayout
        self.postActionFixedWidth = postActionFixedWidth
        self.showNumbers = showNumbers
        self.textStyle = isDetail ? .body : .footnote
        updateMetrics()
        self.data = self.data.filter { action in
            if case .divider = onEnum(of: action) { return useText }
            return true
        }
        rebuild()
    }

    private func updateMetrics() {
        let font = UIFont.preferredFont(forTextStyle: textStyle, compatibleWith: traitCollection)
        let scale = traitCollection.displayScale > 0 ? traitCollection.displayScale : UIScreen.main.scale
        guard metrics.fontSize != font.pointSize || metrics.scale != scale else { return }
        metrics = StatusActionLayoutMetrics(fontSize: font.pointSize,
                                            scale: scale)
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if previousTraitCollection?.preferredContentSizeCategory != traitCollection.preferredContentSizeCategory ||
            previousTraitCollection?.displayScale != traitCollection.displayScale {
            updateMetrics()
            rebuild()
        }
    }

    private var actionContentColor: UIColor {
        textStyle == .body || useText ? .label : .secondaryLabel
    }

    private func rebuild() {
        itemButtonCursor = 0
        groupButtonCursor = 0
        textGroupCursor = 0
        dividerCursor = 0
        var desired: [UIView] = []

        if useText {
            for (index, item) in data.enumerated() {
                if let v = makeActionView(for: item, position: index, isFixedWidth: false) {
                    desired.append(v)
                }
            }
            updateChildren(desired)
            return
        }

        let isStretched = postActionStyle == .stretch && allowSpacer
        for (index, item) in data.enumerated() {
            if let view = makeActionView(for: item, position: index, isFixedWidth: !isStretched && postActionFixedWidth && index != data.count - 1) {
                desired.append(view)
            }
        }
        updateChildren(desired)
    }

    private func updateChildren(_ desired: [UIView]) {
        syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
        childSizes = desired.map { $0.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)) }
        contentHeight = childSizes.map(\.height).max() ?? 0
        layoutWidth = nil
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard !managedChildren.isEmpty else { return }
        let rtl = effectiveUserInterfaceLayoutDirection == .rightToLeft
        if layoutWidth != bounds.width || layoutRightToLeft != rtl {
            let lastHasNumber: Bool
            if let last = data.last {
                switch onEnum(of: last) {
                case .item(let item): lastHasNumber = item.count != nil
                case .group(let group): lastHasNumber = group.displayItem.count != nil
                case .divider: lastHasNumber = false
                }
            } else {
                lastHasNumber = false
            }
            layoutFrames = metrics.frames(width: bounds.width, sizes: childSizes, style: useText ? .hidden : postActionStyle,
                                          allowSpacer: !useText && allowSpacer, lastHasNumber: lastHasNumber, rightToLeft: rtl)
            layoutWidth = bounds.width
            layoutRightToLeft = rtl
        }
        let offset = (bounds.height - contentHeight) / 2
        let direction: UISemanticContentAttribute = rtl ? .forceRightToLeft : .forceLeftToRight
        for (view, frame) in zip(managedChildren, layoutFrames) {
            if view.semanticContentAttribute != direction { view.semanticContentAttribute = direction }
            let target = frame.offsetBy(dx: 0, dy: offset)
            if view.frame != target { view.frame = target }
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        contentHeight
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }

    override var intrinsicContentSize: CGSize {
        sizeThatFits(CGSize(width: bounds.width > 0 ? bounds.width : 320, height: .greatestFiniteMagnitude))
    }

    func performDeferredPoolCleanup() {
        trimPoolsToActiveCursors()
    }

    func performLightweightPoolCleanup() {
        trimPoolsToActiveCursors()
    }

    func prepareForPoolRemoval() {
        syncManagedSubviews(parent: self, current: &managedChildren, desired: [])
        childSizes = []
        layoutFrames = []
        contentHeight = 0
        layoutWidth = nil
        data = []
        postKey = nil
        itemButtonCursor = 0
        groupButtonCursor = 0
        textGroupCursor = 0
        dividerCursor = 0
        trimPoolsToActiveCursors()
    }

    private func trimPoolsToActiveCursors() {
        Self.trimPool(&itemButtonPool, activeCount: itemButtonCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&groupButtonPool, activeCount: groupButtonCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&textGroupPool, activeCount: textGroupCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&dividerPool, activeCount: dividerCursor)
    }

    private static func trimPool<View: UIView>(
        _ pool: inout [View],
        activeCount: Int,
        prepare: (View) -> Void = { _ in }
    ) {
        guard pool.count > activeCount else { return }
        for view in pool[activeCount...] {
            prepare(view)
            view.removeFromSuperview()
        }
        pool.removeLast(pool.count - activeCount)
    }

    // MARK: - Action view factory (matches the switch in StatusActionView)

    private func makeActionView(for action: ActionMenu, position: Int, isFixedWidth: Bool) -> UIView? {
        switch onEnum(of: action) {
        case .item(let item):
            return makeItemButton(item: item, position: position, isFixedWidth: isFixedWidth)
        case .group(let group):
            if useText {
                return textGroupView(group: group)
            } else {
                return makeGroupButton(group: group, position: position, isFixedWidth: isFixedWidth)
            }
        case .divider:
            return divider()
        }
    }

    private func divider() -> UIView {
        while dividerPool.count <= dividerCursor {
            let view = UIView()
            view.backgroundColor = .separator
            dividerPool.append(view)
        }
        let view = dividerPool[dividerCursor]
        dividerCursor += 1
        return view
    }

    private func makeItemButton(item: ActionMenu.Item, position: Int, isFixedWidth: Bool) -> UIView {
        let title: String?
        if useText, let text = item.text?.resolvedString {
            title = text
        } else if showNumbers, let count = item.count?.humanized, !count.isEmpty {
            title = count
        } else {
            title = nil
        }
        while itemButtonPool.count <= itemButtonCursor {
            itemButtonPool.append(ActionItemControl())
        }
        let control = itemButtonPool[itemButtonCursor]
        itemButtonCursor += 1
        if control.isEnabled != item.enabled { control.isEnabled = item.enabled }
        control.configure(
            image: (item.icon ?? (useText ? nil : .more)).flatMap { metrics.iconImage($0) },
            title: title,
            tintColor: item.color?.uiColor ?? actionContentColor,
            metrics: metrics,
            reservesNumberWidth: isFixedWidth && item.count != nil,
            usesExpandedHitArea: !useText,
            accessibilityLabel: item.text?.resolvedString
                ?? String(localized: "more", defaultValue: "More"),
            accessibilityValue: showNumbers ? item.count?.humanized : nil,
            animationState: useText ? nil : StatusActionAnimationState(
                identity: .init(postKey: postKey, item: item, position: position),
                item: item,
                showNumbers: showNumbers
            )
        ) { [weak self] in
            guard let self = self else { return }
            let gen = UIImpactFeedbackGenerator(style: .medium)
            gen.prepare(); gen.impactOccurred()
            item.onClicked(ClickContext(launcher: self.makeLauncher()))
        }
        return control
    }

    private func makeGroupButton(group: ActionMenu.Group, position: Int, isFixedWidth: Bool) -> UIView {
        let title: String?
        if let text = group.displayItem.count?.humanized, showNumbers, !text.isEmpty {
            title = text
        } else {
            title = nil
        }
        while groupButtonPool.count <= groupButtonCursor {
            groupButtonPool.append(ActionItemControl())
        }
        let control = groupButtonPool[groupButtonCursor]
        groupButtonCursor += 1
        if control.isEnabled != group.displayItem.enabled { control.isEnabled = group.displayItem.enabled }
        control.configure(
            image: metrics.iconImage(group.displayItem.icon ?? .more),
            title: title,
            tintColor: group.displayItem.color?.uiColor ?? actionContentColor,
            metrics: metrics,
            reservesNumberWidth: isFixedWidth && group.displayItem.count != nil,
            usesExpandedHitArea: !useText,
            accessibilityLabel: group.displayItem.text?.resolvedString
                ?? String(localized: "more", defaultValue: "More"),
            accessibilityValue: showNumbers ? group.displayItem.count?.humanized : nil,
            animationState: StatusActionAnimationState(
                identity: .init(postKey: postKey, item: group.displayItem, position: position),
                item: group.displayItem,
                showNumbers: showNumbers
            )
        )
        if !control.showsMenuAsPrimaryAction { control.showsMenuAsPrimaryAction = true }
        control.menu = UIMenu(children: [
            UIDeferredMenuElement.uncached { [weak self] completion in
                guard let self else {
                    completion([])
                    return
                }
                completion(self.buildMenu(from: group.actions))
            }
        ])
        return control
    }

    private func textGroupView(group: ActionMenu.Group) -> UIView {
        while textGroupPool.count <= textGroupCursor {
            textGroupPool.append(ActionGroupColumnView())
        }
        let view = textGroupPool[textGroupCursor]
        textGroupCursor += 1
        view.configure(
            actions: Array(group.actions),
            postActionStyle: postActionStyle,
            showNumbers: showNumbers,
            onOpenURL: { [weak self] url in self?.onOpenURL?(url) }
        )
        return view
    }

    private func buildMenu(from actions: [ActionMenu]) -> [UIMenuElement] {

        var children: [UIMenuElement] = []
        var section: [UIMenuElement] = []
        var hasDivider = false

        func flushSection() {
            guard !section.isEmpty else { return }
            children.append(UIMenu(title: "", options: .displayInline, children: section))
            section = []
        }

        for action in actions {
            switch onEnum(of: action) {
            case .item(let item):
                let title: String
                if let text = item.text?.resolvedString {
                    title = text
                } else {
                    title = ""
                }
                let image = item.icon.flatMap { UIImage(fontAwesome: $0.fontAwesomeIcon) }
                var attributes: UIMenuElement.Attributes = item.color == .red ? .destructive : []
                if !item.enabled {
                    attributes.insert(.disabled)
                }
                let uiAction = UIAction(
                    title: title,
                    image: image,
                    attributes: attributes
                ) { [weak self] _ in
                    guard let self = self else { return }
                    let gen = UIImpactFeedbackGenerator(style: .medium)
                    gen.prepare(); gen.impactOccurred()
                    item.onClicked(ClickContext(launcher: self.makeLauncher()))
                }
                section.append(uiAction)
            case .group(let g):
                let subtitle: String
                if let text = g.displayItem.text?.resolvedString {
                    subtitle = text
                } else {
                    subtitle = ""
                }
                section.append(UIMenu(title: subtitle, children: buildMenu(from: g.actions)))
            case .divider:
                hasDivider = true
                flushSection()
            }
        }
        if hasDivider {
            flushSection()
            return children
        }
        return section
    }

    func contextMenu(data: [ActionMenu], postActionLayout: PostActionLayoutConfig) -> UIMenu {
        let actions = castActionMenus(PostActionLayoutHelpers.shared.apply(actions: data, config: postActionLayout))
        return UIMenu(children: buildMenu(from: actions))
    }

    private func makeLauncher() -> AppleUriLauncher {
        AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })
    }
}

private final class ActionGroupColumnView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let topDivider = UIView()
    private let nestedActions = StatusActionsUIView()
    private let bottomDivider = UIView()
    private static let dividerHeight: CGFloat = 1.0 / UIScreen.main.scale
    private static let spacing: CGFloat = 4

    override init(frame: CGRect) {
        super.init(frame: frame)
        topDivider.backgroundColor = .separator
        bottomDivider.backgroundColor = .separator
        addSubview(topDivider)
        addSubview(nestedActions)
        addSubview(bottomDivider)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func prepareForPoolRemoval() {
        nestedActions.prepareForPoolRemoval()
    }

    func configure(
        actions: [ActionMenu],
        postActionStyle: PostActionStyle,
        showNumbers: Bool,
        onOpenURL: ((URL) -> Void)?
    ) {
        nestedActions.onOpenURL = onOpenURL
        nestedActions.configure(
            data: actions,
            useText: true,
            allowSpacer: false,
            postActionStyle: postActionStyle,
            applyPostActionLayout: false,
            showNumbers: showNumbers,
            isDetail: false
        )
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let dh = Self.dividerHeight
        var y: CGFloat = 0
        topDivider.frame = CGRect(x: 0, y: y, width: w, height: dh)
        y += dh + Self.spacing
        let actionsH = childHeight(of: nestedActions, for: w)
        nestedActions.frame = CGRect(x: 0, y: y, width: w, height: actionsH)
        y += actionsH + Self.spacing
        bottomDivider.frame = CGRect(x: 0, y: y, width: w, height: dh)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let dh = Self.dividerHeight
        let actionsH = childHeight(of: nestedActions, for: width)
        let total = dh + Self.spacing + actionsH + Self.spacing + dh
        return ceil(total)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }
}

private final class ActionItemControl: UIButton, ManualLayoutMeasurable, TimelineHeightProviding {
    private let iconView = UIImageView()
    private let label = UILabel()
    private var metrics = StatusActionLayoutMetrics(fontSize: 13, scale: UIScreen.main.scale)
    private var preferredSize = CGSize.zero
    private var horizontalInset: CGFloat = 0
    private var currentSpacing: CGFloat = 0
    private var reservesIconWidth = true
    private var iconScale: CGFloat = 1
    private var onTap: (() -> Void)?
    private var animationState: StatusActionAnimationState?
    private var outgoingIconView: UIImageView?
    private var outgoingLabel: UILabel?

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func commonInit() {
        configuration = nil
        contentHorizontalAlignment = .leading
        contentVerticalAlignment = .center
        directionalLayoutMargins = .zero
        layoutMargins = .zero

        iconView.contentMode = .center
        iconView.isUserInteractionEnabled = false
        addSubview(iconView)

        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 1
        label.lineBreakMode = .byTruncatingTail
        label.isUserInteractionEnabled = false
        addSubview(label)

        addTarget(self, action: #selector(onTapped), for: .touchUpInside)
    }

    func configure(
        image: UIImage?,
        title: String?,
        tintColor: UIColor,
        metrics: StatusActionLayoutMetrics,
        reservesNumberWidth: Bool,
        usesExpandedHitArea: Bool,
        accessibilityLabel: String? = nil,
        accessibilityValue: String? = nil,
        animationState: StatusActionAnimationState? = nil,
        onTap: (() -> Void)? = nil
    ) {
        let reduceMotion = UIAccessibility.isReduceMotionEnabled
        let effects = animationState?.effects(from: self.animationState, isVisible: window != nil, reduceMotion: reduceMotion)
        let iconEffect = effects?.icon ?? .none
        let numberEffect = effects?.number ?? .none
        if animationState?.identity == nil || animationState?.identity != self.animationState?.identity || window == nil || reduceMotion || !isEnabled {
            removeContentAnimations()
        } else {
            if image == nil || (iconEffect == .none && animationState?.icon != self.animationState?.icon) {
                removeIconAnimations()
            }
            if title == nil || (numberEffect == .none && label.text != title) {
                removeNumberAnimations()
            }
        }
        if iconEffect != .none {
            removeIconAnimations()
            if let previousImage = iconView.image {
                let outgoing = resolvedOutgoingIconView()
                outgoing.image = previousImage
                outgoing.tintColor = iconView.tintColor
                outgoing.preferredSymbolConfiguration = iconView.preferredSymbolConfiguration
            }
        }
        if numberEffect != .none {
            removeNumberAnimations()
            let outgoing = resolvedOutgoingLabel()
            outgoing.text = label.text
            outgoing.font = label.font
            outgoing.textColor = label.textColor
            outgoing.textAlignment = label.textAlignment
        }
        self.animationState = animationState
        self.metrics = metrics
        iconScale = metrics.iconScale(imageWidth: image?.size.width ?? 0)
        reservesIconWidth = usesExpandedHitArea || image != nil
        self.onTap = onTap
        self.tintColor = tintColor
        self.currentSpacing = reservesIconWidth && (title != nil || reservesNumberWidth) ? StatusActionLayoutMetrics.labelSpacing : 0
        self.horizontalInset = usesExpandedHitArea ? 4 : 0
        self.accessibilityLabel = accessibilityLabel
        self.accessibilityValue = accessibilityValue
        iconView.tintColor = tintColor
        iconView.image = image
        iconView.isHidden = image == nil

        label.text = title
        label.font = metrics.nativeFont
        label.textColor = tintColor
        label.isHidden = title == nil
        let titleWidth = title.map { _ in
            let width = label.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)).width
            return ceil(width * metrics.scale) / metrics.scale
        }
        preferredSize = metrics.size(titleWidth: titleWidth, reservesNumberWidth: reservesNumberWidth,
                                     hasIcon: reservesIconWidth, expanded: usesExpandedHitArea)

        invalidateIntrinsicContentSize()
        setNeedsLayout()
        if iconEffect != .none || numberEffect != .none {
            layoutIfNeeded()
        }
        if iconEffect != .none {
            if let outgoing = outgoingIconView, outgoing.image != nil {
                if iconEffect == .pop {
                    addSpring(to: outgoing.layer, keyPath: "transform.scale", from: Double(iconScale), to: 0,
                              spring: StatusActionAnimationTiming.fadeSpring, key: "statusAction.pop")
                }
                addSpring(to: outgoing.layer, keyPath: "opacity", from: 1, to: 0,
                          spring: StatusActionAnimationTiming.fadeSpring, key: "statusAction.iconFade")
            }
            if iconEffect == .pop {
                addSpring(to: iconView.layer, keyPath: "transform.scale", from: StatusActionAnimationTiming.iconStartScale * Double(iconScale), to: Double(iconScale),
                          spring: StatusActionAnimationTiming.iconSpring, key: "statusAction.pop")
            }
            addSpring(to: iconView.layer, keyPath: "opacity", from: 0, to: 1,
                      spring: StatusActionAnimationTiming.fadeSpring, key: "statusAction.iconFade")
        }
        if numberEffect != .none, let outgoing = outgoingLabel {
            let offset = Double(label.bounds.height) * (numberEffect == .up ? 1 : -1)
            addSpring(to: label.layer, keyPath: "transform.translation.y", from: offset, to: 0,
                      spring: StatusActionAnimationTiming.numberSpring, key: "statusAction.numberSlide")
            addSpring(to: outgoing.layer, keyPath: "transform.translation.y", from: 0, to: -offset,
                      spring: StatusActionAnimationTiming.numberSpring, key: "statusAction.numberSlide")
            addSpring(to: label.layer, keyPath: "opacity", from: 0, to: 1,
                      spring: StatusActionAnimationTiming.fadeSpring, key: "statusAction.numberFade")
            addSpring(to: outgoing.layer, keyPath: "opacity", from: 1, to: 0,
                      spring: StatusActionAnimationTiming.fadeSpring, key: "statusAction.numberFade")
        }
    }

    func prepareForPoolRemoval() {
        animationState = nil
        removeContentAnimations()
        onTap = nil
        menu = nil
        showsMenuAsPrimaryAction = false
        iconView.image = nil
        label.text = nil
        preferredSize = .zero
        horizontalInset = 0
        accessibilityLabel = nil
        accessibilityValue = nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            animationState = nil
            removeContentAnimations()
        }
    }

    private func removeContentAnimations() {
        removeIconAnimations()
        removeNumberAnimations()
    }

    private func removeIconAnimations() {
        iconView.layer.removeAnimation(forKey: "statusAction.pop")
        iconView.layer.removeAnimation(forKey: "statusAction.iconFade")
        outgoingIconView?.layer.removeAnimation(forKey: "statusAction.pop")
        outgoingIconView?.layer.removeAnimation(forKey: "statusAction.iconFade")
        outgoingIconView?.image = nil
    }

    private func removeNumberAnimations() {
        label.layer.removeAnimation(forKey: "statusAction.numberSlide")
        label.layer.removeAnimation(forKey: "statusAction.numberFade")
        outgoingLabel?.layer.removeAnimation(forKey: "statusAction.numberSlide")
        outgoingLabel?.layer.removeAnimation(forKey: "statusAction.numberFade")
        outgoingLabel?.text = nil
    }

    private func resolvedOutgoingIconView() -> UIImageView {
        if let outgoingIconView { return outgoingIconView }
        let view = UIImageView()
        view.contentMode = .center
        view.alpha = 0
        view.isUserInteractionEnabled = false
        view.accessibilityElementsHidden = true
        insertSubview(view, belowSubview: iconView)
        outgoingIconView = view
        return view
    }

    private func resolvedOutgoingLabel() -> UILabel {
        if let outgoingLabel { return outgoingLabel }
        let view = UILabel()
        view.numberOfLines = 1
        view.lineBreakMode = label.lineBreakMode
        view.alpha = 0
        view.isUserInteractionEnabled = false
        view.accessibilityElementsHidden = true
        insertSubview(view, belowSubview: label)
        outgoingLabel = view
        return view
    }

    private func addSpring(to layer: CALayer, keyPath: String, from: Double, to: Double, spring: Spring, key: String) {
        let animation = CASpringAnimation(keyPath: keyPath)
        animation.fromValue = from
        animation.toValue = to
        animation.mass = spring.mass
        animation.stiffness = spring.stiffness
        animation.damping = spring.damping
        animation.duration = spring.settlingDuration
        // The spring supplies the easing; its clock must remain linear.
        animation.timingFunction = CAMediaTimingFunction(name: .linear)
        layer.add(animation, forKey: key)
    }

    @objc private func onTapped() {
        onTap?()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let rtl = effectiveUserInterfaceLayoutDirection == .rightToLeft
        let iconWidth = reservesIconWidth ? StatusActionLayoutMetrics.iconWidth : 0
        if !iconView.transform.isIdentity { iconView.transform = .identity }
        iconView.frame = CGRect(x: rtl ? bounds.width - horizontalInset - iconWidth : horizontalInset,
                                y: (bounds.height - metrics.iconHeight) / 2,
                                width: iconWidth, height: metrics.iconHeight)
        if iconScale != 1 { iconView.transform = CGAffineTransform(scaleX: iconScale, y: iconScale) }
        let titleX = rtl ? horizontalInset : horizontalInset + iconWidth + currentSpacing
        label.frame = CGRect(x: titleX, y: (bounds.height - metrics.lineHeight) / 2,
                             width: max(0, bounds.width - horizontalInset * 2 - iconWidth - currentSpacing),
                             height: metrics.lineHeight)
        label.isHidden = label.text == nil || label.bounds.width == 0
        let alignment: NSTextAlignment = rtl ? .right : .left
        if label.textAlignment != alignment { label.textAlignment = alignment }
        if let outgoingIconView {
            outgoingIconView.bounds = iconView.bounds
            outgoingIconView.center = iconView.center
            outgoingIconView.transform = iconView.transform
        }
        if let outgoingLabel {
            var frame = label.frame
            frame.size.width = max(frame.width, outgoingLabel.intrinsicContentSize.width)
            if rtl { frame.origin.x = label.frame.maxX - frame.width }
            outgoingLabel.frame = frame
            outgoingLabel.isHidden = label.isHidden
        }
    }

    override var intrinsicContentSize: CGSize { preferredSize }

    override func sizeThatFits(_ size: CGSize) -> CGSize { preferredSize }

    func timelineHeight(for width: CGFloat) -> CGFloat? { preferredSize.height }

    override func systemLayoutSizeFitting(_ targetSize: CGSize) -> CGSize { preferredSize }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize { preferredSize }

    override var isHighlighted: Bool {
        didSet { alpha = isEnabled ? (isHighlighted ? 0.55 : 1) : 0.4 }
    }

    override var isEnabled: Bool {
        didSet { alpha = isEnabled ? 1 : 0.4 }
    }
}

import SwiftUI // for OpenURLAction

private func castActionMenus(_ value: Any) -> [ActionMenu] {
    if let actions = value as? [ActionMenu] {
        return actions
    }
    if let actions = value as? NSArray {
        return actions.cast(ActionMenu.self)
    }
    return []
}
