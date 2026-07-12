import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
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
    @Environment(\.openURL) private var openURL
    #if os(macOS)
    @ScaledMetric(relativeTo: .callout) private var fontSize = 16
    #else
    @ScaledMetric(relativeTo: .footnote) private var fontSize = 13
    #endif
    private let data: [ActionMenu]
    private let useText: Bool
    private let allowSpacer: Bool
    private let applyPostActionLayout: Bool

    public init(data: [ActionMenu], useText: Bool, allowSpacer: Bool = true, applyPostActionLayout: Bool = true) {
        self.data = data
        self.useText = useText
        self.allowSpacer = allowSpacer
        self.applyPostActionLayout = applyPostActionLayout
    }

    public var body: some View {
        let actions = resolvedData
        if useText {
            ForEach(0..<actions.count, id: \.self) { index in
                StatusActionView(
                    data: actions[index],
                    useText: true,
                    isFixedWidth: false,
                    fontSize: fontSize,
                    showNumbers: showNumbers,
                    openURL: openURL
                )
            }
        } else {
            HStack {
                ForEach(0..<actions.count, id: \.self) { index in
                    let item = actions[index]
                    if (index == actions.count - 1 && postActionStyle == .leftAligned) ||
                        (postActionStyle == .rightAligned && index == 0) ||
                        (postActionStyle == .stretch && index != 0) {
                        if allowSpacer {
                            Spacer()
                        }
                    }
                    StatusActionView(
                        data: item,
                        useText: useText,
                        isFixedWidth: index != actions.count - 1,
                        fontSize: fontSize,
                        showNumbers: showNumbers,
                        openURL: openURL
                    )
                }
            }
            .labelIconToTitleSpacingIfAvailable(4)
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
    private let fontSize: CGFloat
    private let showNumbers: Bool
    private let openURL: OpenURLAction

    public init(
        data: ActionMenu,
        useText: Bool,
        isFixedWidth: Bool,
        fontSize: CGFloat,
        showNumbers: Bool,
        openURL: OpenURLAction
    ) {
        self.data = data
        self.useText = useText
        self.isFixedWidth = isFixedWidth
        self.fontSize = fontSize
        self.showNumbers = showNumbers
        self.openURL = openURL
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .item(let item):
            StatusActionItemView(
                data: item,
                useText: useText,
                isFixedWidth: isFixedWidth,
                fontSize: fontSize,
                showNumbers: showNumbers,
                openURL: openURL
            )
        case .group(let group):
            if useText {
                Divider()
                ForEach(0..<group.actions.count, id: \.self) { index in
                    StatusActionView(
                        data: group.actions[index],
                        useText: true,
                        isFixedWidth: false,
                        fontSize: fontSize,
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
                            fontSize: fontSize,
                            showNumbers: showNumbers,
                            openURL: openURL
                        )
                    }
                } label: {
                    Group {
                        if let text = group.displayItem.count?.humanized, showNumbers {
                            Label {
                                Text(text)
                                    .lineLimit(1)
                                    .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                            } icon: {
                                StatusActionIcon(icon: group.displayItem.icon)
                            }
                        } else {
                            StatusActionIcon(icon: group.displayItem.icon)
                                .frame(minWidth: StatusActionHitArea.labelContentHeight(fontSize: fontSize))
                                .contentShape(Rectangle())
                        }
                    }
                    .statusActionContentPadding(isExpanded: true, fontSize: fontSize)
                }
                .optionalForegroundStyle(group.displayItem.color?.swiftColor)
                .buttonStyle(.plain)
                .macOSStatusActionHoverStyle(isEnabled: true)
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
    private let fontSize: CGFloat
    private let showNumbers: Bool
    private let openURL: OpenURLAction

    public init(
        data: ActionMenu.Item,
        useText: Bool,
        isFixedWidth: Bool,
        fontSize: CGFloat,
        showNumbers: Bool,
        openURL: OpenURLAction
    ) {
        self.data = data
        self.useText = useText
        self.isFixedWidth = isFixedWidth
        self.fontSize = fontSize
        self.showNumbers = showNumbers
        self.openURL = openURL
    }

    private var resolvedText: Text? {
        if useText, let text = data.text?.resolvedString {
            return Text(text)
        } else if showNumbers, let count = data.count?.humanized {
            return Text(count)
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
            .optionalForegroundStyle(data.color?.swiftColor)
            .buttonStyle(.plain)
            .macOSStatusActionHoverStyle(isEnabled: !useText)
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
        Group {
            if let text = resolvedText {
                Label {
                    text.frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                } icon: {
                    StatusActionIcon(icon: data.icon)
                }
            } else {
                StatusActionIcon(icon: data.icon)
            }
        }
        .statusActionContentPadding(isExpanded: !useText, fontSize: fontSize)
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

private enum StatusActionHitArea {
    static let horizontalInset: CGFloat = 6
    static let verticalInset: CGFloat = 4

    static func labelContentHeight(fontSize: CGFloat) -> CGFloat {
        fontSize + 2
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
    func labelIconToTitleSpacingIfAvailable(_ spacing: CGFloat) -> some View {
        self.backport.flareLabelIconToTitleSpacing(spacing)
    }

    @ViewBuilder
    func optionalForegroundStyle(_ color: Color?) -> some View {
        if let color {
            self.foregroundStyle(color)
        } else {
            self
        }
    }

    @ViewBuilder
    func statusActionContentPadding(isExpanded: Bool, fontSize: CGFloat) -> some View {
        if isExpanded {
            self
                .frame(minHeight: StatusActionHitArea.labelContentHeight(fontSize: fontSize))
                .padding(.horizontal, StatusActionHitArea.horizontalInset)
                .padding(.vertical, StatusActionHitArea.verticalInset)
                .contentShape(Rectangle())
        } else {
            self.contentShape(Rectangle())
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
    var swiftColor: Color? {
        switch self {
        case .red: return .red
        case .contentColor: return .primary
        case .primaryColor: return .accentColor
        }
    }

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

private struct MacCrossPostActionKey: EnvironmentKey {
    static let defaultValue: MacCrossPostAction? = nil
}

public extension EnvironmentValues {
    var macCrossPostAction: MacCrossPostAction? {
        get { self[MacCrossPostActionKey.self] }
        set { self[MacCrossPostActionKey.self] = newValue }
    }
}

struct MacStatusShareMenu<LabelContent: View>: View {
    let data: MacStatusShareData
    let onShareScreenshot: () -> Void
    @ViewBuilder let label: () -> LabelContent
    @Environment(\.openURL) private var openURL
    @Environment(\.macCrossPostAction) private var crossPost

    init(
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

public struct MacStatusShareSheet: View {
    private let statusKey: MicroBlogKey
    private let purpose: MacStatusSharePurpose
    private let onCrossPost: ((Data, String) -> Void)?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var theme: ColorScheme?
    @State private var screenshotURL: URL?
    @State private var isRenderingScreenshot = false
    @State private var renderRequestID: UUID?
    @State private var alert: MacStatusShareAlert?
    @State private var crossPostDispatched = false

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
                presenter: StatusPresenter(
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
                    MacStatusSharePreview(
                        data: state,
                        statusKey: statusKey,
                        colorScheme: resolvedColorScheme,
                        timelineAppearance: timelineAppearance
                    )
                    .allowsHitTesting(false)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                Divider()

                footer
                .padding(20)
            }
            .frame(width: 560, height: 720)
            .onAppear {
                renderScreenshot(data: state)
            }
            .onChange(of: theme) { _, _ in
                renderScreenshot(data: state)
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
        switch purpose {
        case .shareScreenshot:
            VStack(alignment: .leading, spacing: 16) {
                Text("appearance_theme")
                    .font(.headline)

                Picker("appearance_theme", selection: $theme) {
                    Text("appearance_theme_system").tag(nil as ColorScheme?)
                    Text("appearance_theme_light").tag(Optional(ColorScheme.light))
                    Text("appearance_theme_dark").tag(Optional(ColorScheme.dark))
                }
                .labelsHidden()
                .pickerStyle(.segmented)

                HStack {
                    Button("Cancel") {
                        dismiss()
                    }

                    Spacer()

                    if let screenshotURL {
                        ShareLink(item: screenshotURL) {
                            Text("done")
                        }
                        .buttonStyle(.borderedProminent)
                    } else {
                        Button {} label: {
                            if isRenderingScreenshot {
                                ProgressView()
                                    .controlSize(.small)
                            } else {
                                Text("done")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(true)
                    }
                }
            }
        case .crossPost:
            HStack {
                Button("Cancel") {
                    dismiss()
                }

                Spacer()

                ProgressView()
                    .controlSize(.small)
                Text("share_crosspost")
            }
        }
    }

    private func renderScreenshot(data: UiTimelineV2) {
        guard !crossPostDispatched else { return }
        let requestID = UUID()
        renderRequestID = requestID
        screenshotURL = nil
        isRenderingScreenshot = true

        Task { @MainActor in
            do {
                let screenshot = try await MacStatusShareScreenshotRenderer.render(
                    view: MacStatusSharePreview(
                        data: data,
                        statusKey: statusKey,
                        colorScheme: resolvedColorScheme,
                        timelineAppearance: timelineAppearance
                    ),
                    fileName: "flare-status-\(statusKey.description()).png"
                )
                guard renderRequestID == requestID else { return }
                switch purpose {
                case .shareScreenshot:
                    screenshotURL = screenshot.fileURL
                case .crossPost:
                    crossPostDispatched = true
                    onCrossPost?(screenshot.data, screenshot.fileURL.lastPathComponent)
                    try? FileManager.default.removeItem(
                        at: screenshot.fileURL.deletingLastPathComponent()
                    )
                }
            } catch {
                guard renderRequestID == requestID else { return }
                alert = MacStatusShareAlert(message: error.localizedDescription)
            }
            guard renderRequestID == requestID else { return }
            isRenderingScreenshot = false
        }
    }
}

private struct MacStatusShareScreenshot {
    let data: Data
    let fileURL: URL
}

private struct MacStatusSharePreview: View {
    let data: UiTimelineV2
    let statusKey: MicroBlogKey
    let colorScheme: ColorScheme
    let timelineAppearance: TimelineAppearance

    var body: some View {
        TimelineView(
            data: data,
            detailStatusKey: statusKey,
            showTranslate: false
        )
        .frame(width: 360, alignment: .leading)
        .padding()
        .background(Color.flareSecondarySystemGroupedBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .shadow(radius: 8)
        .padding(64)
        .background(Color.flareSystemGroupedBackground)
        .environment(\.colorScheme, colorScheme)
        .environment(\.timelineAppearance, timelineAppearance.withSharePreviewDefaults())
    }
}

private enum MacStatusShareScreenshotRenderer {
    @MainActor
    static func render<Content: View>(
        view: Content,
        fileName: String
    ) async throws -> MacStatusShareScreenshot {
        let scale: CGFloat = 2
        let hostingView = NSHostingView(rootView: view.fixedSize(horizontal: false, vertical: true))
        let fittingSize = hostingView.fittingSize
        guard fittingSize.width > 0, fittingSize.height > 0 else {
            throw MacStatusShareError.invalidSnapshotSize
        }

        hostingView.frame = NSRect(origin: .zero, size: fittingSize)
        hostingView.wantsLayer = true
        hostingView.layer?.contentsScale = scale
        hostingView.layoutSubtreeIfNeeded()
        try? await Task.sleep(nanoseconds: 100_000_000)
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

private extension TimelineAppearance {
    func withSharePreviewDefaults() -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: showMedia,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: true,
            expandMediaSize: expandMediaSize,
            limitMediaGridToNine: limitMediaGridToNine,
            videoAutoplay: .never,
            showLinkPreview: showLinkPreview,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: postActionStyle,
            postActionLayout: postActionLayout,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: timelineDisplayMode,
            aiConfig: aiConfig,
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}
#endif
