import SwiftUI
import KotlinSharedUI
import FlareAppleCore

#if canImport(UIKit)
import UIKit
#endif

#if os(macOS)
import AppKit
#endif

// MARK: - Top-level container
// Hoists @ScaledMetric, @Environment reads to a single place
// instead of duplicating them in every child view instance.

public struct StatusActionsView: View {
    @Environment(\.timelineAppearance.postActionStyle) private var postActionStyle
    @Environment(\.timelineAppearance.showNumbers) private var showNumbers
    @Environment(\.timelineAppearance.postActionLayout) private var postActionLayout
    @Environment(\.timelineAppearance.postActionFixedWidth) private var postActionFixedWidth
    @Environment(\.openURL) private var openURL
    #if os(macOS)
    @ScaledMetric(relativeTo: .callout) private var fontSize = NSFont.preferredFont(forTextStyle: .callout).pointSize
    @ScaledMetric(relativeTo: .body) private var bodyFontSize = NSFont.preferredFont(forTextStyle: .body).pointSize
    #else
    @ScaledMetric(relativeTo: .footnote) private var fontSize = 13
    @ScaledMetric(relativeTo: .body) private var bodyFontSize = 17
    #endif
    private let data: [ActionMenu]
    private let useText: Bool
    private let allowSpacer: Bool
    private let applyPostActionLayout: Bool
    private let topPadding: CGFloat
    private let isDetail: Bool
    private let postKey: String?
    @Environment(\.displayScale) private var displayScale

    public init(
        data: [ActionMenu],
        useText: Bool,
        allowSpacer: Bool = true,
        applyPostActionLayout: Bool = true,
        topPadding: CGFloat = 0,
        isDetail: Bool = false,
        postKey: String? = nil
    ) {
        self.data = data
        self.useText = useText
        self.allowSpacer = allowSpacer
        self.applyPostActionLayout = applyPostActionLayout
        self.topPadding = topPadding
        self.isDetail = isDetail
        self.postKey = postKey
    }

    public var body: some View {
        let metrics = StatusActionLayoutMetrics(fontSize: isDetail || useText ? bodyFontSize : fontSize, scale: displayScale)
        let actions = resolvedData.filter { action in
            if case .divider = onEnum(of: action) { return useText }
            return true
        }
        if useText {
            ForEach(0..<actions.count, id: \.self) { index in
                StatusActionView(data: actions[index], useText: true, isFixedWidth: false,
                                 metrics: metrics, showNumbers: showNumbers, openURL: openURL)
            }
        } else if !actions.isEmpty {
            let isStretched = postActionStyle == .stretch && allowSpacer
            StatusActionRowLayout(metrics: metrics, style: postActionStyle, allowSpacer: allowSpacer,
                                  lastHasNumber: hasTrailingCount(actions)) {
                ForEach(0..<actions.count, id: \.self) { index in
                    StatusActionView(data: actions[index], useText: false,
                                     isFixedWidth: !isStretched && postActionFixedWidth && index != actions.count - 1,
                                     metrics: metrics, showNumbers: showNumbers, openURL: openURL,
                                     animationIdentity: animationIdentity(for: actions[index], position: index))
                }
            }
            .font(.system(size: metrics.fontSize))
            .padding(.top, topPadding)
        }
    }

    private func hasTrailingCount(_ actions: [ActionMenu]) -> Bool {
        guard let last = actions.last else { return false }
        switch onEnum(of: last) {
        case .item(let item): return item.count != nil
        case .group(let group): return group.displayItem.count != nil
        case .divider: return false
        }
    }

    private func animationIdentity(for action: ActionMenu, position: Int) -> StatusActionAnimationState.Identity? {
        switch onEnum(of: action) {
        case .item(let item): return .init(postKey: postKey, item: item, position: position)
        case .group(let group): return .init(postKey: postKey, item: group.displayItem, position: position)
        case .divider: return nil
        }
    }

    private var resolvedData: [ActionMenu] {
        guard applyPostActionLayout else { return data }
        return castActionMenus(
            PostActionLayoutHelpers.shared.apply(
                actions: data,
                config: postActionLayout
            )
        )
    }
}

// MARK: - Single action (sealed class switch)
// No @Environment or @ScaledMetric — values passed from parent.

public struct StatusActionView: View {
    private let data: ActionMenu
    private let useText: Bool
    private let isFixedWidth: Bool
    private let metrics: StatusActionLayoutMetrics
    private let showNumbers: Bool
    private let openURL: OpenURLAction
    private let animationIdentity: StatusActionAnimationState.Identity?

    public init(
        data: ActionMenu,
        useText: Bool,
        isFixedWidth: Bool,
        metrics: StatusActionLayoutMetrics,
        showNumbers: Bool,
        openURL: OpenURLAction,
        animationIdentity: StatusActionAnimationState.Identity? = nil
    ) {
        self.data = data
        self.useText = useText
        self.isFixedWidth = isFixedWidth
        self.metrics = metrics
        self.showNumbers = showNumbers
        self.openURL = openURL
        self.animationIdentity = animationIdentity
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .item(let item):
            StatusActionItemView(
                data: item,
                useText: useText,
                isFixedWidth: isFixedWidth,
                metrics: metrics,
                showNumbers: showNumbers,
                openURL: openURL,
                animationIdentity: animationIdentity
            )
        case .group(let group):
            if useText {
                Divider()
                ForEach(0..<group.actions.count, id: \.self) { index in
                    StatusActionView(
                        data: group.actions[index],
                        useText: true,
                        isFixedWidth: false,
                        metrics: metrics,
                        showNumbers: showNumbers,
                        openURL: openURL
                    )
                }
                Divider()
            } else {
                Menu {
                    ForEach(0..<group.actions.count, id: \.self) { index in
                        StatusActionView(
                            data: group.actions[index],
                            useText: true,
                            isFixedWidth: false,
                            metrics: metrics,
                            showNumbers: showNumbers,
                            openURL: openURL
                        )
                    }
                } label: {
                    StatusActionBarLabel(
                        state: StatusActionAnimationState(identity: animationIdentity, item: group.displayItem, showNumbers: showNumbers),
                        metrics: metrics,
                        reservesNumberWidth: isFixedWidth && group.displayItem.count != nil
                    )
                }
                .buttonStyle(StatusActionButtonStyle())
                .accessibilityLabel(
                    Text(
                        verbatim: group.displayItem.text?.resolvedString
                            ?? FlareAppleUILocalization.string("more", fallback: "More")
                    )
                )
                .accessibilityValue(
                    Text(verbatim: showNumbers ? (group.displayItem.count?.humanized ?? "") : "")
                )
                .disabled(!group.displayItem.enabled)
                .macOSStatusActionHoverStyle(isEnabled: group.displayItem.enabled)

            }
        case .divider:
            Divider()
        }
    }
}

// MARK: - Leaf action item (Button)
// Computes display text once to avoid nested _ConditionalContent branches.

public struct StatusActionItemView: View {
    private let data: ActionMenu.Item
    private let useText: Bool
    private let isFixedWidth: Bool
    private let metrics: StatusActionLayoutMetrics
    private let showNumbers: Bool
    private let openURL: OpenURLAction
    private let animationIdentity: StatusActionAnimationState.Identity?

    public init(
        data: ActionMenu.Item,
        useText: Bool,
        isFixedWidth: Bool,
        metrics: StatusActionLayoutMetrics,
        showNumbers: Bool,
        openURL: OpenURLAction,
        animationIdentity: StatusActionAnimationState.Identity? = nil
    ) {
        self.data = data
        self.useText = useText
        self.isFixedWidth = isFixedWidth
        self.metrics = metrics
        self.showNumbers = showNumbers
        self.openURL = openURL
        self.animationIdentity = animationIdentity
    }

    private var resolvedTitle: String? {
        if useText, let text = data.text?.resolvedString {
            return text
        } else if showNumbers, let count = data.count?.humanized, !count.isEmpty {
            return count
        }
        return nil
    }

    private func triggerHapticFeedback() {
        #if os(iOS)
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
        #endif
    }

    public var body: some View {
        actionControl
            .buttonStyle(StatusActionButtonStyle())
            .accessibilityLabel(
                Text(
                    verbatim: data.text?.resolvedString
                        ?? FlareAppleUILocalization.string("more", fallback: "More")
                )
            )
            .accessibilityValue(
                Text(verbatim: showNumbers ? (data.count?.humanized ?? "") : "")
            )
            .disabled(!data.enabled)
            .macOSStatusActionHoverStyle(isEnabled: !useText && data.enabled)

    }

    @ViewBuilder
    private var actionControl: some View {
        #if os(macOS)
        if let shareData = macStatusShareData {
            MacStatusShareMenu(
                data: shareData,
                onShareScreenshot: {
                    data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
            ) {
                actionLabel
            }
        } else {
            actionButton
        }
        #else
        actionButton
        #endif
    }

    private var actionButton: some View {
        Button(role: data.color?.role) {
            triggerHapticFeedback()
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        } label: {
            actionLabel
        }
    }

    @ViewBuilder
    private var actionLabel: some View {
        if useText {
            Group {
                if let title = resolvedTitle {
                    Label { Text(title) } icon: { StatusActionIcon(icon: data.icon) }
                } else {
                    StatusActionIcon(icon: data.icon)
                }
            }
            .optionalForegroundStyle(data.color?.swiftColor)
        } else {
            StatusActionBarLabel(
                state: StatusActionAnimationState(identity: animationIdentity, item: data, showNumbers: showNumbers),
                metrics: metrics,
                reservesNumberWidth: isFixedWidth && data.count != nil
            )
        }
    }

    #if os(macOS)
    private var macStatusShareData: MacStatusShareData? {
        guard let actionFamily = data.actionFamily,
              actionFamily == .share || actionFamily == .fxShare
        else {
            return nil
        }
        guard case .deeplink(let clickEvent) = onEnum(of: data.clickEvent),
              let route = DeeplinkRoute.companion.parse(uri: clickEvent.url),
              case .status(let status) = onEnum(of: route),
              case .shareSheet(let shareSheet) = onEnum(of: status)
        else {
            return nil
        }
        return MacStatusShareData(
            statusKey: shareSheet.statusKey,
            accountType: shareSheet.accountType,
            shareUrl: shareSheet.shareUrl,
            fxShareUrl: shareSheet.fxShareUrl,
            fixvxShareUrl: shareSheet.fixvxShareUrl
        )
    }
    #endif
}

// MARK: - Helpers

private struct StatusActionButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(isEnabled ? (configuration.isPressed ? 0.55 : 1) : 0.4)
    }
}

private struct StatusActionBarLabel: View {
    let state: StatusActionAnimationState
    let metrics: StatusActionLayoutMetrics
    let reservesNumberWidth: Bool
    #if os(iOS)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isVisible = false
    @State private var motion: StatusActionMotion?
    #endif

    var body: some View {
        #if os(iOS)
        let activeMotion = motion?.target == state ? motion : nil
        let animateIcon = activeMotion.map { $0.effects.icon != .none } ?? false
        let animateNumber = activeMotion.map { $0.effects.number != .none } ?? false
        #endif
        StatusActionContentLayout(metrics: metrics,
                                  preferredSize: metrics.size(titleWidth: state.text.map(metrics.textWidth), reservesNumberWidth: reservesNumberWidth, expanded: false)) {
            Group {
                #if os(iOS)
                if let image = metrics.iconImage(state.icon ?? .more) {
                    StatusActionIcon(icon: state.icon ?? .more)
                        .scaleEffect(metrics.iconScale(imageWidth: image.size.width))
                }
                #else
                StatusActionIcon(icon: state.icon ?? .more)
                #endif
            }
            .optionalForegroundStyle(state.color?.swiftColor)
            #if os(iOS)
            .opacity(animateIcon ? 0 : 1)
            .transaction { $0.animation = nil; $0.disablesAnimations = true }
            .overlay {
                if let activeMotion, animateIcon {
                    StatusActionIconMotion(motion: activeMotion)
                        .scaleEffect(metrics.iconScale(imageWidth: metrics.iconImage(state.icon ?? .more)?.size.width ?? 0))
                        .transition(.identity)
                        .accessibilityHidden(true)
                        .allowsHitTesting(false)
                }
            }
            #endif
            .frame(width: StatusActionLayoutMetrics.iconWidth, height: metrics.iconHeight)
            if let text = state.text {
                Text(text)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .optionalForegroundStyle(state.color?.swiftColor)
                    #if os(iOS)
                    .opacity(animateNumber ? 0 : 1)
                    .transaction { $0.animation = nil; $0.disablesAnimations = true }
                    .overlay(alignment: .leading) {
                        if let activeMotion, animateNumber {
                            StatusActionNumberMotion(motion: activeMotion)
                                .transition(.identity)
                                .accessibilityHidden(true)
                                .allowsHitTesting(false)
                        }
                    }
                    #endif
            } else if reservesNumberWidth {
                Color.clear.accessibilityHidden(true)
            }
        }
        .padding(StatusActionLayoutMetrics.padding)
        .contentShape(Rectangle())
        #if os(iOS)
        .onAppear {
            resetAnimations()
            isVisible = true
        }
        .onDisappear {
            isVisible = false
            resetAnimations()
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { resetAnimations() }
        }
        .onChange(of: state) { previous, newState in
            let effects = newState.effects(from: previous, isVisible: isVisible, reduceMotion: reduceMotion)
            if effects.icon == .none && effects.number == .none {
                resetAnimations()
            } else {
                // Only real state changes create animation layers; static rows keep their identity.
                motion = StatusActionMotion(previous: previous, target: newState, effects: effects)
            }
        }
        #endif
    }

    #if os(iOS)
    private func resetAnimations() {
        if motion != nil { motion = nil }
    }
    #endif
}

#if os(iOS)
private struct StatusActionMotion {
    let eventID = UUID()
    let previous: StatusActionAnimationState
    let target: StatusActionAnimationState
    let effects: StatusActionAnimationState.Effects
}

private struct StatusActionIconMotion: View {
    let motion: StatusActionMotion
    @State private var trigger = 0

    var body: some View {
        let enteringScale = motion.effects.icon == .pop ? StatusActionAnimationTiming.iconStartScale : 1
        ZStack {
            StatusActionIcon(icon: motion.previous.icon)
                .optionalForegroundStyle(motion.previous.color?.swiftColor)
                .keyframeAnimator(initialValue: StatusActionMotionValues(progress: 1, opacity: 1), trigger: trigger) { content, value in
                    content.scaleEffect(value.progress).opacity(value.opacity)
                } keyframes: { _ in
                    KeyframeTrack(\.progress) {
                        MoveKeyframe(1)
                        SpringKeyframe(motion.effects.icon == .pop ? 0 : 1, spring: StatusActionAnimationTiming.fadeSpring)
                    }
                    KeyframeTrack(\.opacity) {
                        MoveKeyframe(1)
                        SpringKeyframe(0, spring: StatusActionAnimationTiming.fadeSpring)
                    }
                }
            StatusActionIcon(icon: motion.target.icon)
                .optionalForegroundStyle(motion.target.color?.swiftColor)
                .keyframeAnimator(initialValue: StatusActionMotionValues(progress: enteringScale, opacity: 0), trigger: trigger) { content, value in
                    content.scaleEffect(value.progress).opacity(value.opacity)
                } keyframes: { _ in
                    KeyframeTrack(\.progress) {
                        MoveKeyframe(enteringScale)
                        SpringKeyframe(1, spring: StatusActionAnimationTiming.iconSpring)
                    }
                    KeyframeTrack(\.opacity) {
                        MoveKeyframe(0)
                        SpringKeyframe(1, spring: StatusActionAnimationTiming.fadeSpring)
                    }
                }
        }
        .onAppear { trigger += 1 }
        .onChange(of: motion.eventID) { _, _ in trigger += 1 }
    }
}

private struct StatusActionMotionValues {
    var progress: Double
    var opacity: Double
}

private struct StatusActionNumberMotion: View {
    let motion: StatusActionMotion
    @State private var trigger = 0

    var body: some View {
        let direction: CGFloat = motion.effects.number == .up ? -1 : 1
        ZStack(alignment: .leading) {
            if let previous = motion.previous.text {
                Text(previous)
                    .lineLimit(1)
                    .optionalForegroundStyle(motion.previous.color?.swiftColor)
                    .keyframeAnimator(initialValue: StatusActionMotionValues(progress: 0, opacity: 1), trigger: trigger) { content, value in
                        content.visualEffect { effect, geometry in
                            effect.offset(y: geometry.size.height * direction * CGFloat(value.progress))
                        }
                        .opacity(value.opacity)
                    } keyframes: { _ in
                        KeyframeTrack(\.progress) {
                            MoveKeyframe(0)
                            SpringKeyframe(1, spring: StatusActionAnimationTiming.numberSpring)
                        }
                        KeyframeTrack(\.opacity) {
                            MoveKeyframe(1)
                            SpringKeyframe(0, spring: StatusActionAnimationTiming.fadeSpring)
                        }
                    }
            }
            if let target = motion.target.text {
                Text(target)
                    .lineLimit(1)
                    .optionalForegroundStyle(motion.target.color?.swiftColor)
                    .keyframeAnimator(initialValue: StatusActionMotionValues(progress: 0, opacity: 0), trigger: trigger) { content, value in
                        content.visualEffect { effect, geometry in
                            effect.offset(y: -geometry.size.height * direction * CGFloat(1 - value.progress))
                        }
                        .opacity(value.opacity)
                    } keyframes: { _ in
                        KeyframeTrack(\.progress) {
                            MoveKeyframe(0)
                            SpringKeyframe(1, spring: StatusActionAnimationTiming.numberSpring)
                        }
                        KeyframeTrack(\.opacity) {
                            MoveKeyframe(0)
                            SpringKeyframe(1, spring: StatusActionAnimationTiming.fadeSpring)
                        }
                    }
            }
        }
        .onAppear { trigger += 1 }
        .onChange(of: motion.eventID) { _, _ in trigger += 1 }
    }
}
#endif

private struct StatusActionContentLayout: Layout {
    let metrics: StatusActionLayoutMetrics
    let preferredSize: CGSize

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? preferredSize.width
        return CGSize(width: width.isFinite ? width : preferredSize.width, height: preferredSize.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let iconWidth = StatusActionLayoutMetrics.iconWidth
        subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading,
                          proposal: ProposedViewSize(width: iconWidth, height: metrics.iconHeight))
        if subviews.count > 1 {
            let titleWidth = max(0, bounds.width - iconWidth - StatusActionLayoutMetrics.labelSpacing)
            let titleX = bounds.minX + iconWidth + StatusActionLayoutMetrics.labelSpacing
            subviews[1].place(at: CGPoint(x: titleX, y: bounds.midY), anchor: .leading,
                              proposal: ProposedViewSize(width: titleWidth, height: metrics.lineHeight))
        }
    }
}

private struct StatusActionRowLayout: Layout {
    let metrics: StatusActionLayoutMetrics
    let style: PostActionStyle
    let allowSpacer: Bool
    let lastHasNumber: Bool

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let naturalWidth = sizes.reduce(CGFloat.zero) { $0 + $1.width } +
            CGFloat(max(sizes.count - 1, 0)) * StatusActionLayoutMetrics.actionSpacing
        return CGSize(width: allowSpacer ? (proposal.width ?? naturalWidth) : min(naturalWidth, proposal.width ?? naturalWidth),
                      height: sizes.map(\.height).max() ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let frames = metrics.frames(width: bounds.width, sizes: sizes, style: style, allowSpacer: allowSpacer,
                                    lastHasNumber: lastHasNumber)
        for (index, subview) in subviews.enumerated() {
            let frame = frames[index]
            subview.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY), anchor: .topLeading,
                          proposal: ProposedViewSize(frame.size))
        }
    }
}

#if os(macOS)
private struct StatusActionHoverModifier: ViewModifier {
    let isEnabled: Bool
    @State private var isHovered = false

    func body(content: Content) -> some View {
        if isEnabled {
            content
                .background {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(Color.primary.opacity(isHovered ? 0.08 : 0))
                }
                .animation(.easeOut(duration: 0.12), value: isHovered)
                .onHover { hovering in
                    isHovered = hovering
                }
        } else {
            content
        }
    }
}
#endif

private extension View {
    @ViewBuilder
    func optionalForegroundStyle(_ color: Color?) -> some View {
        // Apply only to leaf content so animation state stays above this color branch.
        if let color {
            foregroundStyle(color)
        } else {
            self
        }
    }

    @ViewBuilder
    func macOSStatusActionHoverStyle(isEnabled: Bool) -> some View {
        #if os(macOS)
        modifier(StatusActionHoverModifier(isEnabled: isEnabled))
        #else
        self
        #endif
    }
}

public extension ActionMenu.ItemColor {
    var role: ButtonRole? {
        switch self {
        case .red:
                .destructive
        case .primaryColor:
            #if os(iOS)
            if #available(iOS 26.0, *) {
                    .confirm
            } else {
                nil
            }
            #else
            nil
            #endif
        default:
            nil
        }
    }
}

public struct StatusActionIcon: View {
    private let icon: UiIcon?

    public init(icon: UiIcon?) {
        self.icon = icon
    }

    public var body: some View {
        if let icon = icon {
            icon.image
        }
    }
}

private func castActionMenus(_ value: Any) -> [ActionMenu] {
    if let actions = value as? [ActionMenu] {
        return actions
    }
    if let actions = value as? NSArray {
        return actions.cast(ActionMenu.self)
    }
    return []
}

#if os(macOS)
public struct MacStatusShareData {
    public let statusKey: MicroBlogKey
    public let accountType: AccountType
    public let shareUrl: String
    public let fxShareUrl: String?
    public let fixvxShareUrl: String?

    public init(
        statusKey: MicroBlogKey,
        accountType: AccountType,
        shareUrl: String,
        fxShareUrl: String? = nil,
        fixvxShareUrl: String? = nil
    ) {
        self.statusKey = statusKey
        self.accountType = accountType
        self.shareUrl = shareUrl
        self.fxShareUrl = fxShareUrl
        self.fixvxShareUrl = fixvxShareUrl
    }
}

public typealias MacCrossPostAction = (MacStatusShareData) -> Void

public extension EnvironmentValues {
    @Entry var macCrossPostAction: MacCrossPostAction? = nil
}

public struct MacStatusShareMenu<LabelContent: View>: View {
    let data: MacStatusShareData
    let onShareScreenshot: () -> Void
    @ViewBuilder let label: () -> LabelContent
    @Environment(\.openURL) private var openURL
    @Environment(\.macCrossPostAction) private var crossPost

    public init(
        data: MacStatusShareData,
        onShareScreenshot: @escaping () -> Void,
        @ViewBuilder label: @escaping () -> LabelContent
    ) {
        self.data = data
        self.onShareScreenshot = onShareScreenshot
        self.label = label
    }

    public var body: some View {
        Menu {
            Button {
                crossPost?(data)
            } label: {
                Label {
                    Text("share_crosspost")
                } icon: {
                    Image(fontAwesome: .retweet)
                }
            }
            .disabled(crossPost == nil)

            if let shareURL = URL(string: data.shareUrl) {
                ShareLink(item: shareURL) {
                    Label("share_link", systemImage: "link")
                }
            }

            Button {
                onShareScreenshot()
            } label: {
                Label("share_screenshot", systemImage: "photo")
            }

            if let fxShareUrl = data.fxShareUrl,
               let fxShareURL = URL(string: fxShareUrl) {
                ShareLink(item: fxShareURL) {
                    Label("share_via_fxembed", systemImage: "link")
                }
            }

            if let fixvxShareUrl = data.fixvxShareUrl,
               let fixvxShareURL = URL(string: fixvxShareUrl) {
                ShareLink(item: fixvxShareURL) {
                    Label("share_via_fixvx", systemImage: "link")
                }
            }

            Divider()

            Button {
                copyLink()
            } label: {
                Label("share_copy_link", systemImage: "doc.on.doc")
            }

            if let shareURL = URL(string: DeeplinkRoute.OpenLinkDirectly(url: data.shareUrl).toUri()) {
                Button {
                    openURL(shareURL)
                } label: {
                    Label("deep_link_account_picker_open_in_browser", systemImage: "safari")
                }
            }
        } label: {
            label()
        }
    }

    private func copyLink() {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(data.shareUrl, forType: .string)
    }
}

public enum MacStatusSharePurpose {
    case shareScreenshot
    case crossPost
}

private enum MacStatusSharePreviewStyle: Hashable {
    case card
    case content
}

public struct MacStatusShareSheet: View {
    private let statusKey: MicroBlogKey
    private let purpose: MacStatusSharePurpose
    private let onCrossPost: ((Data, String) -> Void)?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @State private var presenter: KotlinPresenter<StatusSharePresenterState>
    @State private var theme: ColorScheme?
    @State private var previewStyle: MacStatusSharePreviewStyle = .card
    @State private var screenshotURL: URL?
    @State private var isRenderingScreenshot = false
    @State private var renderRequestID: UUID?
    @State private var alert: MacStatusShareAlert?
    @State private var crossPostDispatched = false
    @State private var renderedRevision: String?
    @State private var renderAttempt = 0

    private var previewRevision: String {
        let statusHash: Int32 = if case .success(let data) = onEnum(of: presenter.state.status) { data.data.renderHash } else { 0 }
        return "\(statusHash)-\(Array(presenter.state.selectedReplies).map { $0.renderHash })-\(resolvedColorScheme)-\(previewStyle)-\(renderAttempt)"
    }

    private var currentScreenshotURL: URL? { renderedRevision == previewRevision ? screenshotURL : nil }

    public init(
        statusKey: MicroBlogKey,
        accountType: AccountType,
        purpose: MacStatusSharePurpose = .shareScreenshot,
        onCrossPost: ((Data, String) -> Void)? = nil
    ) {
        self.statusKey = statusKey
        self.purpose = purpose
        self.onCrossPost = onCrossPost
        _presenter = .init(
            wrappedValue: .init(
                presenter: StatusSharePresenter(
                    accountType: accountType,
                    statusKey: statusKey
                )
            )
        )
    }

    public var body: some View {
        StateView(state: presenter.state.status) { state in
            VStack(spacing: 0) {
                ScrollView {
                    if let url = currentScreenshotURL, let image = NSImage(contentsOf: url) {
                        Image(nsImage: image).resizable().scaledToFit()
                    } else if isRenderingScreenshot {
                        ProgressView("share_replies_preparing")
                            .padding()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                Divider()
                footer.padding(20)
            }
            .frame(width: 560, height: 720)
            .task(id: previewRevision) {
                await renderScreenshot(data: state)
            }
        } errorContent: { error in
            ContentUnavailableView {
                Label("share_screenshot_failed", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error.message ?? String(localized: "share_screenshot_failed"))
            } actions: {
                Button("Cancel") {
                    dismiss()
                }
            }
            .frame(width: 560, height: 720)
        } loadingContent: {
            TimelinePlaceholderView()
                .frame(width: 560, height: 720)
        }
        .navigationTitle(navigationTitle)
        .sheet(isPresented: Binding(
            get: { presenter.state.selecting },
            set: { if !$0 { presenter.state.cancelSelection() } }
        )) {
            StatusShareReplyPicker(state: presenter.state)
        }
        .alert(item: $alert) { alert in
            Alert(
                title: Text("share_screenshot_failed"),
                message: Text(verbatim: alert.message),
                dismissButton: .default(Text("Ok"))
            )
        }
    }

    private var resolvedColorScheme: ColorScheme {
        theme ?? colorScheme
    }

    private var navigationTitle: LocalizedStringKey {
        switch purpose {
        case .shareScreenshot:
            "share_screenshot"
        case .crossPost:
            "share_crosspost"
        }
    }

    @ViewBuilder
    private var footer: some View {
        VStack(alignment: .leading, spacing: 16) {
            GroupBox {
                VStack(spacing: 12) {
                    LabeledContent("appearance_layout_group_title") {
                        Picker("appearance_layout_group_title", selection: $previewStyle) {
                            Text("appearance_timeline_display_mode_card").tag(MacStatusSharePreviewStyle.card)
                            Text("status_share_sheet_preview_content").tag(MacStatusSharePreviewStyle.content)
                        }
                        .labelsHidden()
                        .pickerStyle(.segmented)
                    }
                    LabeledContent("appearance_theme") {
                        Picker("appearance_theme", selection: $theme) {
                            Text("appearance_theme_system").tag(nil as ColorScheme?)
                            Text("appearance_theme_light").tag(Optional(ColorScheme.light))
                            Text("appearance_theme_dark").tag(Optional(ColorScheme.dark))
                        }
                        .labelsHidden()
                        .pickerStyle(.segmented)
                    }
                    Divider()
                    HStack {
                        Text("share_replies_title", bundle: FlareAppleUILocalization.bundle)
                        Spacer()
                        Button { presenter.state.selectReplies() } label: {
                            Text(String(format: String(localized: "share_replies_count", bundle: FlareAppleUILocalization.bundle), Int(presenter.state.selectedCount)))
                        }
                    }
                }
                .padding(8)
            }
            HStack {
                Button("Cancel") { dismiss() }
                Spacer()
                if let url = currentScreenshotURL {
                    switch purpose {
                    case .shareScreenshot:
                        ShareLink(item: url) { Text("done") }.buttonStyle(.borderedProminent)
                    case .crossPost:
                        Button("share_crosspost") {
                            do {
                                let data = try Data(contentsOf: url)
                                crossPostDispatched = true
                                onCrossPost?(data, url.lastPathComponent)
                            } catch {
                                alert = MacStatusShareAlert(message: error.localizedDescription)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(crossPostDispatched)
                    }
                } else if isRenderingScreenshot {
                    ProgressView().controlSize(.small)
                } else {
                    Button("share_replies_retry") { renderAttempt += 1 }
                }
            }
        }
    }

    private func renderScreenshot(data: UiTimelineV2) async {
        guard !crossPostDispatched else { return }
        let requestID = UUID()
        let revision = previewRevision
        renderRequestID = requestID
        screenshotURL = nil
        isRenderingScreenshot = true
        do {
            let screenshot = try await MacStatusShareScreenshotRenderer.render(
                view: MacStatusSharePreview(
                    data: data,
                    statusKey: statusKey,
                    replies: Array(presenter.state.selectedReplies),
                    colorScheme: resolvedColorScheme,
                    previewStyle: previewStyle,
                    timelineAppearance: timelineAppearance
                ),
                fileName: "flare-status-\(statusKey.description()).png"
            )
            guard !Task.isCancelled, renderRequestID == requestID, revision == previewRevision else {
                try? FileManager.default.removeItem(at: screenshot.fileURL.deletingLastPathComponent())
                return
            }
            screenshotURL = screenshot.fileURL
            renderedRevision = revision
        } catch is CancellationError {
        } catch {
            guard renderRequestID == requestID else { return }
            alert = MacStatusShareAlert(message: error.localizedDescription)
        }
        guard renderRequestID == requestID else { return }
        isRenderingScreenshot = false
    }
}

private struct MacStatusShareScreenshot {
    let data: Data
    let fileURL: URL
}

private struct MacStatusSharePreview: View {
    let data: UiTimelineV2
    let statusKey: MicroBlogKey
    let replies: [UiTimelineV2]
    let colorScheme: ColorScheme
    let previewStyle: MacStatusSharePreviewStyle
    let timelineAppearance: TimelineAppearance

    var body: some View {
        StatusShareContent(data: data, statusKey: statusKey, replies: replies)
        .frame(width: 360, alignment: .leading)
        .padding()
        .background(Color.flareSecondarySystemGroupedBackground)
        .if(previewStyle == .card) { view in
            view
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .contentShape(RoundedRectangle(cornerRadius: 16))
                .shadow(radius: 8)
                .padding(64)
        }
        .background(Color.flareSystemGroupedBackground)
        .environment(\.colorScheme, colorScheme)
        .environment(\.timelineAppearance, timelineAppearance)
    }
}

private enum MacStatusShareScreenshotRenderer {
    @MainActor
    static func render<Content: View>(
        view: Content,
        fileName: String
    ) async throws -> MacStatusShareScreenshot {
        let scale: CGFloat = 2
        let loading = ShareImageLoading()
        let hostingView = NSHostingView(rootView: view.environment(\.shareImageLoading, loading).fixedSize(horizontal: false, vertical: true))
        // Screenshot padding must not inherit the window's safe area.
        hostingView.safeAreaRegions = []
        var fittingSize = hostingView.fittingSize
        try ShareImageLoading.checkSize(fittingSize, scale: scale)
        guard fittingSize.width > 0, fittingSize.height > 0 else {
            throw MacStatusShareError.invalidSnapshotSize
        }

        hostingView.frame = NSRect(origin: CGPoint(x: -fittingSize.width * 2, y: 0), size: fittingSize)
        guard let container = NSApp.keyWindow?.contentView else { throw StatusShareRenderError.mediaUnavailable }
        container.addSubview(hostingView)
        defer { hostingView.removeFromSuperview() }
        hostingView.wantsLayer = true
        hostingView.layer?.contentsScale = scale
        hostingView.layoutSubtreeIfNeeded()
        try await Task.sleep(for: .milliseconds(100))
        try await loading.awaitReady()
        try await Task.sleep(for: .milliseconds(50))
        fittingSize = hostingView.fittingSize
        try ShareImageLoading.checkSize(fittingSize, scale: scale)
        hostingView.frame = NSRect(origin: CGPoint(x: -fittingSize.width * 2, y: 0), size: fittingSize)
        hostingView.layoutSubtreeIfNeeded()

        let pixelWidth = max(1, Int((fittingSize.width * scale).rounded(.up)))
        let pixelHeight = max(1, Int((fittingSize.height * scale).rounded(.up)))
        guard let representation = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: pixelWidth,
            pixelsHigh: pixelHeight,
            bitsPerSample: 8,
            samplesPerPixel: 4,
            hasAlpha: true,
            isPlanar: false,
            colorSpaceName: .deviceRGB,
            bytesPerRow: 0,
            bitsPerPixel: 0
        ) else {
            throw MacStatusShareError.snapshotFailed
        }
        representation.size = fittingSize
        hostingView.cacheDisplay(in: hostingView.bounds, to: representation)

        guard let pngData = representation.representation(using: .png, properties: [:]) else {
            throw MacStatusShareError.pngEncodingFailed
        }

        let directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("flare-status-share-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)

        let fileURL = directoryURL.appendingPathComponent(
            MediaFileNamePolicy.shared.safeLocalFileName(value: fileName, fallback: "flare-status.png")
        )
        try pngData.write(to: fileURL, options: .atomic)
        return MacStatusShareScreenshot(data: pngData, fileURL: fileURL)
    }
}

private enum MacStatusShareError: LocalizedError {
    case invalidSnapshotSize
    case snapshotFailed
    case pngEncodingFailed

    var errorDescription: String? {
        switch self {
        case .invalidSnapshotSize:
            String(localized: "share_screenshot_invalid_size")
        case .snapshotFailed:
            String(localized: "share_screenshot_failed")
        case .pngEncodingFailed:
            String(localized: "share_screenshot_encoding_failed")
        }
    }
}

private struct MacStatusShareAlert: Identifiable {
    let id = UUID()
    let message: String
}

#endif
