import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import UniformTypeIdentifiers
import FlareAppleCore
import FlareAppleUI

struct StatusShareSheet: View {
    let statusKey: MicroBlogKey
    let accountType: AccountType
    let shareUrl: String
    let fxShareUrl: String?
    let fixvxShareUrl: String?
    
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var renderScale: CGFloat = 2.0
    @State private var theme: ColorScheme? = nil
    @State private var image: UIImage? = nil
    
    init(statusKey: MicroBlogKey, accountType: AccountType, shareUrl: String, fxShareUrl: String?, fixvxShareUrl: String?) {
        self.statusKey = statusKey
        self.accountType = accountType
        self.shareUrl = shareUrl
        self.fxShareUrl = fxShareUrl
        self.fixvxShareUrl = fixvxShareUrl
        self._presenter = .init(wrappedValue: .init(presenter: StatusPresenter(accountType: accountType, statusKey: statusKey)))
    }
    
    var body: some View {
        List {
            StateView(state: presenter.state.status) { data in
                ViewBox {
                    ZStack {
                        previewView(data: data)
                            .padding()
                        Color.clear
                            .contentShape(Rectangle())
                            .onTapGesture {
                            }
                    }
                }
                .frame(minHeight: 200, maxHeight: 360)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
                
                Section {
                    if let url = URL(string: shareUrl) {
                        ShareLink(item: url) {
                            Label("share_link", systemImage: "link")
                        }
                    }
                    
                    if let image {
                        ShareLink(item: Image(uiImage: image), preview: SharePreview("share_screenshot", image: Image(uiImage: image))) {
                            Label("share_screenshot", systemImage: "photo")
                        }
                    }

                    Button {
                        saveImage()
                    } label: {
                        Label("save_screenshot", systemImage: "arrow.down.circle")
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
                
                Picker("appearance_theme", selection: $theme) {
                    Text("appearance_theme_system").tag(nil as ColorScheme?)
                    Text("appearance_theme_light").tag(Optional(ColorScheme.light))
                    Text("appearance_theme_dark").tag(Optional(ColorScheme.dark))
                }
            } loadingContent: {
                TimelinePlaceholderView()
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
        }
        .navigationTitle("fx_share")
        .onSuccessOf(of: presenter.state.status) { data in
            Task {
                image = await renderImage(data: data)
            }
        }
        .onChange(of: theme) { oldValue, newValue in
            if case .success(let data) = onEnum(of: presenter.state.status) {
                Task {
                    image = await renderImage(data: data.data)
                }
            }
        }
    }
    
    @ViewBuilder
    private func previewView(data: UiTimelineV2) -> some View {
        TimelineView(data: data, detailStatusKey: statusKey, showTranslate: false)
            .frame(width: 360)
            .padding()
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(.rect(cornerRadius: 16))
            .contentShape(RoundedRectangle(cornerRadius: 16))
            .shadow(radius: 8)
            .padding(64)
            .background(Color(.systemGroupedBackground))
            .environment(\.colorScheme, theme ?? colorScheme)
            .environment(\.timelineAppearance, timelineAppearance.withSharePreviewDefaults())
    }
    
    
    private func renderImage(data: UiTimelineV2) async -> UIImage? {
        return await previewView(data: data).snapshot(
            colorScheme: theme ?? colorScheme
        )
    }
    
    private func saveImage() {
        guard let image = image else { return }
        MediaSaver.shared.saveImage(image)
        dismiss()
    }
}

extension TimelineAppearance {
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

extension View {
    @MainActor
    func snapshot(
        colorScheme: ColorScheme,
        proposedWidth: CGFloat = 520,
        delay: TimeInterval = 0.1
    ) async -> UIImage? {
        guard proposedWidth.isFinite, proposedWidth > 0 else {
            return nil
        }

        let controller = UIHostingController(rootView: self.edgesIgnoringSafeArea(.top))
        controller.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        controller.sizingOptions = [.intrinsicContentSize]
        guard let view = controller.view else {
            return nil
        }
        view.backgroundColor = .systemGroupedBackground

        guard let initialSize = controller.snapshotSize(proposedWidth: proposedWidth) else {
            return nil
        }
        view.prepareForSnapshot(size: initialSize)
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))

        guard let targetSize = controller.snapshotSize(proposedWidth: proposedWidth) else {
            return nil
        }
        view.prepareForSnapshot(size: targetSize)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        guard targetSize.isValidSnapshotSize(scale: format.scale) else {
            return nil
        }
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { context in
            if !view.drawHierarchy(in: view.bounds, afterScreenUpdates: true) {
                view.layer.render(in: context.cgContext)
            }
        }
    }
}

@MainActor
private extension UIHostingController {
    func snapshotSize(proposedWidth: CGFloat) -> CGSize? {
        let measuredSize = sizeThatFits(
            in: CGSize(
                width: proposedWidth,
                height: UIView.layoutFittingExpandedSize.height
            )
        )
        let size = CGSize(width: ceil(measuredSize.width), height: ceil(measuredSize.height))
        return size.width.isFinite && size.height.isFinite && size.width > 0 && size.height > 0
            ? size
            : nil
    }
}

@MainActor
private extension UIView {
    func prepareForSnapshot(size: CGSize) {
        bounds = CGRect(origin: .zero, size: size)
        setNeedsLayout()
        layoutIfNeeded()
    }
}

private extension CGSize {
    func isValidSnapshotSize(scale: CGFloat) -> Bool {
        let pixelWidth = width * scale
        let pixelHeight = height * scale
        let pixelCount = pixelWidth * pixelHeight
        return width.isFinite &&
            height.isFinite &&
            width > 0 &&
            height > 0 &&
            pixelWidth <= 16_384 &&
            pixelHeight <= 16_384 &&
            pixelCount <= 64_000_000
    }
}
