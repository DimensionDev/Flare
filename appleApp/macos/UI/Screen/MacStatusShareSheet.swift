import AppKit
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI

private let macStatusShareSheetWidth: CGFloat = 920
private let macStatusShareSheetHeight: CGFloat = 620
private let macStatusSharePreviewPaneWidth: CGFloat = 520
private let macStatusSharePreviewTimelineWidth: CGFloat = 360
private let macStatusShareScreenshotScale: CGFloat = 2
private let macStatusSharePreviewCornerRadius: CGFloat = 16

struct MacStatusShareSheet: View {
    let statusKey: MicroBlogKey
    let accountType: AccountType
    let shareUrl: String
    let fxShareUrl: String?
    let fixvxShareUrl: String?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var theme: ColorScheme?
    @State private var screenshotURL: URL?
    @State private var isRenderingScreenshot = false
    @State private var alert: MacStatusShareAlert?

    init(
        statusKey: MicroBlogKey,
        accountType: AccountType,
        shareUrl: String,
        fxShareUrl: String?,
        fixvxShareUrl: String?
    ) {
        self.statusKey = statusKey
        self.accountType = accountType
        self.shareUrl = shareUrl
        self.fxShareUrl = fxShareUrl
        self.fixvxShareUrl = fixvxShareUrl
        _presenter = .init(
            wrappedValue: .init(
                presenter: StatusPresenter(
                    accountType: accountType,
                    statusKey: statusKey
                )
            )
        )
    }

    var body: some View {
        StateView(state: presenter.state.status) { data in
            HStack(spacing: 0) {
                previewPane(data: data)
                    .frame(width: macStatusSharePreviewPaneWidth)

                Divider()

                optionsPane
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(width: macStatusShareSheetWidth, height: macStatusShareSheetHeight)
            .onAppear {
                renderScreenshot(data: data)
            }
            .onChange(of: theme) { _, _ in
                renderScreenshot(data: data)
            }
        } loadingContent: {
            TimelinePlaceholderView()
                .frame(width: macStatusShareSheetWidth, height: macStatusShareSheetHeight)
        }
        .navigationTitle("status_menu_share")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("done_button") {
                    dismiss()
                }
            }
        }
        .alert(item: $alert) { alert in
            Alert(
                title: Text(alert.title),
                message: Text(verbatim: alert.message),
                dismissButton: .default(Text("OK"))
            )
        }
    }

    private func previewPane(data: UiTimelineV2) -> some View {
        ScrollView {
            HStack {
                Spacer(minLength: 0)
                MacStatusSharePreview(
                    data: data,
                    statusKey: statusKey,
                    colorScheme: resolvedColorScheme,
                    timelineAppearance: timelineAppearance
                )
                .allowsHitTesting(false)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
        }
    }

    private var optionsPane: some View {
        Form {
            Section {
                if let url = URL(string: shareUrl) {
                    ShareLink(item: url) {
                        Label("share_link", systemImage: "link")
                    }

                    Button {
                        copyToPasteboard(shareUrl)
                    } label: {
                        Label("share_copy_link", systemImage: "doc.on.doc")
                    }
                }

                if let screenshotURL {
                    ShareLink(item: screenshotURL) {
                        Label("share_screenshot", systemImage: "photo")
                    }
                } else {
                    Button {} label: {
                        if isRenderingScreenshot {
                            Label {
                                Text("share_screenshot_rendering")
                            } icon: {
                                ProgressView()
                                    .controlSize(.small)
                            }
                        } else {
                            Label("share_screenshot", systemImage: "photo")
                        }
                    }
                    .disabled(true)
                }

                if let fxShareUrl, let url = URL(string: fxShareUrl) {
                    ShareLink(item: url) {
                        Label("share_via_fxembed", systemImage: "link")
                    }
                }

                if let fixvxShareUrl, let url = URL(string: fixvxShareUrl) {
                    ShareLink(item: url) {
                        Label("share_via_fixvx", systemImage: "link")
                    }
                }
            }

            Section {
                Picker("share_theme", selection: $theme) {
                    Text("appearance_theme_system").tag(nil as ColorScheme?)
                    Text("appearance_theme_light").tag(Optional(ColorScheme.light))
                    Text("appearance_theme_dark").tag(Optional(ColorScheme.dark))
                }
            }
        }
        .formStyle(.grouped)
        .scrollContentBackground(.hidden)
    }

    private var resolvedColorScheme: ColorScheme {
        theme ?? colorScheme
    }

    private func renderScreenshot(data: UiTimelineV2) {
        isRenderingScreenshot = true
        screenshotURL = nil

        Task { @MainActor in
            do {
                screenshotURL = try await MacStatusShareScreenshotRenderer.render(
                    view: MacStatusSharePreview(
                        data: data,
                        statusKey: statusKey,
                        colorScheme: resolvedColorScheme,
                        timelineAppearance: timelineAppearance
                    ),
                    fileName: "flare-status-\(statusKey.description()).png"
                )
            } catch {
                alert = MacStatusShareAlert(
                    title: "share_screenshot_failed",
                    message: error.localizedDescription
                )
            }
            isRenderingScreenshot = false
        }
    }

    private func copyToPasteboard(_ value: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(value, forType: .string)
    }
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
        .frame(width: macStatusSharePreviewTimelineWidth, alignment: .leading)
        .padding()
        .background(Color.flareSecondarySystemGroupedBackground)
        .clipShape(RoundedRectangle(cornerRadius: macStatusSharePreviewCornerRadius))
        .contentShape(RoundedRectangle(cornerRadius: macStatusSharePreviewCornerRadius))
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
    ) async throws -> URL {
        let hostingView = NSHostingView(rootView: view.fixedSize(horizontal: false, vertical: true))
        let fittingSize = hostingView.fittingSize
        guard fittingSize.width > 0, fittingSize.height > 0 else {
            throw MacStatusShareError.invalidSnapshotSize
        }

        hostingView.frame = NSRect(origin: .zero, size: fittingSize)
        hostingView.wantsLayer = true
        hostingView.layer?.contentsScale = macStatusShareScreenshotScale
        hostingView.layoutSubtreeIfNeeded()
        try? await Task.sleep(nanoseconds: 100_000_000)
        hostingView.layoutSubtreeIfNeeded()

        let pixelWidth = max(1, Int((fittingSize.width * macStatusShareScreenshotScale).rounded(.up)))
        let pixelHeight = max(1, Int((fittingSize.height * macStatusShareScreenshotScale).rounded(.up)))
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

        let fileURL = directoryURL.appendingPathComponent(safeFileName(fileName))
        try pngData.write(to: fileURL, options: .atomic)
        return fileURL
    }

    private static func safeFileName(_ value: String) -> String {
        let invalidCharacters = CharacterSet(charactersIn: "/\\?%*|\"<>:")
            .union(.newlines)
            .union(.controlCharacters)
        let fileName = value
            .components(separatedBy: invalidCharacters)
            .joined(separator: "-")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return fileName.isEmpty ? "flare-status.png" : fileName
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
    let title: LocalizedStringKey
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
