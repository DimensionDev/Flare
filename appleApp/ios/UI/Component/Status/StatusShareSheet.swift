import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import UniformTypeIdentifiers
import FlareAppleCore
import FlareAppleUI

private enum SharePreviewStyle: Hashable {
    case card
    case content
}

struct StatusShareSheet: View {
    let statusKey: MicroBlogKey
    let accountType: AccountType
    let shareUrl: String
    let fxShareUrl: String?
    let fixvxShareUrl: String?
    let onCrossPost: (ComposePrefill) -> Void
    
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @State private var presenter: KotlinPresenter<StatusSharePresenterState>
    @State private var theme: ColorScheme? = nil
    @State private var previewStyle: SharePreviewStyle = .card
    @State private var image: UIImage? = nil
    @State private var renderedRevision: String?
    @State private var renderError: String?
    @State private var renderAttempt = 0

    private var previewRevision: String {
        let statusHash: Int32 = if case .success(let data) = onEnum(of: presenter.state.status) { data.data.renderHash } else { 0 }
        return "\(statusHash)-\(Array(presenter.state.selectedReplies).map { $0.renderHash })-\(String(describing: theme ?? colorScheme))-\(previewStyle)-\(renderAttempt)"
    }

    private var currentImage: UIImage? { renderedRevision == previewRevision ? image : nil }
    
    init(
        statusKey: MicroBlogKey,
        accountType: AccountType,
        shareUrl: String,
        fxShareUrl: String?,
        fixvxShareUrl: String?,
        onCrossPost: @escaping (ComposePrefill) -> Void
    ) {
        self.statusKey = statusKey
        self.accountType = accountType
        self.shareUrl = shareUrl
        self.fxShareUrl = fxShareUrl
        self.fixvxShareUrl = fixvxShareUrl
        self.onCrossPost = onCrossPost
        self._presenter = .init(wrappedValue: .init(presenter: StatusSharePresenter(accountType: accountType, statusKey: statusKey)))
    }
    
    var body: some View {
        List {
            StateView(state: presenter.state.status) { data in
                ScrollView {
                    if let currentImage {
                        Image(uiImage: currentImage).resizable().scaledToFit()
                    } else if let renderError {
                        Text(renderError)
                        Button("share_replies_retry") { renderAttempt += 1 }
                    } else {
                        ProgressView("share_replies_preparing")
                    }
                }
                .frame(height: 360)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)

                Section {
                    Button {
                        crossPost()
                    } label: {
                        Label {
                            Text("share_crosspost")
                        } icon: {
                            Image(fontAwesome: .retweet)
                        }
                    }
                    .disabled(currentImage == nil)

                    if let url = URL(string: shareUrl) {
                        ShareLink(item: url) {
                            Label("share_link", systemImage: "link")
                        }
                    }
                    
                    if let image = currentImage {
                        ShareLink(item: Image(uiImage: image), preview: SharePreview("share_screenshot", image: Image(uiImage: image))) {
                            Label("share_screenshot", systemImage: "photo")
                        }
                    }

                    Button {
                        saveImage()
                    } label: {
                        Label("save_screenshot", systemImage: "arrow.down.circle")
                    }
                    .disabled(currentImage == nil)
                    
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
                    Picker("appearance_layout_group_title", selection: $previewStyle) {
                        Text("appearance_timeline_display_mode_card").tag(SharePreviewStyle.card)
                        Text("status_share_sheet_preview_content").tag(SharePreviewStyle.content)
                    }

                    Picker("appearance_theme", selection: $theme) {
                        Text("appearance_theme_system").tag(nil as ColorScheme?)
                        Text("appearance_theme_light").tag(Optional(ColorScheme.light))
                        Text("appearance_theme_dark").tag(Optional(ColorScheme.dark))
                    }

                    Button {
                        presenter.state.selectReplies()
                    } label: {
                        HStack {
                            Text("share_replies_title")
                                .foregroundStyle(.primary)
                            Spacer()
                            Text(String(format: String(localized: "share_replies_count"), Int(presenter.state.selectedCount)))
                                .foregroundStyle(.secondary)
                            Image(systemName: "chevron.forward")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                    }
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
        .sheet(isPresented: Binding(
            get: { presenter.state.selecting },
            set: { if !$0 { presenter.state.cancelSelection() } }
        )) {
            StatusShareReplyPicker(state: presenter.state)
                .presentationDetents([.large])
        }
        .task(id: previewRevision) {
            let revision = previewRevision
            image = nil
            renderError = nil
            guard case .success(let data) = onEnum(of: presenter.state.status) else { return }
            do {
                let result = try await previewView(data: data.data).snapshot(colorScheme: theme ?? colorScheme)
                try Task.checkCancellation()
                guard revision == previewRevision else { return }
                image = result
                renderedRevision = revision
            } catch is CancellationError {
            } catch {
                guard revision == previewRevision else { return }
                renderError = error.localizedDescription
            }
        }
    }
    
    @ViewBuilder
    private func previewView(data: UiTimelineV2) -> some View {
        StatusShareContent(data: data, statusKey: statusKey, replies: Array(presenter.state.selectedReplies))
            .frame(width: 360)
            .padding()
            .background(Color(.secondarySystemGroupedBackground))
            .if(previewStyle == .card) { view in
                view
                    .clipShape(.rect(cornerRadius: 16))
                    .contentShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(radius: 8)
                    .padding(64)
            }
            .background(Color(.systemGroupedBackground))
            .environment(\.colorScheme, theme ?? colorScheme)
            .environment(\.timelineAppearance, timelineAppearance)
    }
    
    
    private func saveImage() {
        guard let image = currentImage else { return }
        MediaSaver.shared.saveImage(image)
        dismiss()
    }

    private func crossPost() {
        guard let imageData = currentImage?.pngData() else { return }
        onCrossPost(
            ComposePrefill(
                text: "\n\n\(shareUrl)",
                cursorPosition: 0,
                imageData: imageData,
                fileName: "flare-crosspost.png"
            )
        )
    }
}

extension View {
    @MainActor
    func snapshot(colorScheme: ColorScheme) async throws -> UIImage {
        let loading = ShareImageLoading()
        let controller = UIHostingController(rootView: self.environment(\.shareImageLoading, loading).fixedSize())
        controller.overrideUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        let view = controller.view!
        var targetSize = view.intrinsicContentSize
        try ShareImageLoading.checkSize(targetSize, scale: 3)
        view.frame = CGRect(origin: CGPoint(x: -targetSize.width * 2, y: 0), size: targetSize)
        view.backgroundColor = .systemGroupedBackground
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).flatMap(\.windows).first(where: \.isKeyWindow) else {
            throw StatusShareRenderError.mediaUnavailable
        }
        window.addSubview(view)
        defer { view.removeFromSuperview() }
        view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(100))
        try await loading.awaitReady()
        try await Task.sleep(for: .milliseconds(50))
        targetSize = view.intrinsicContentSize
        try ShareImageLoading.checkSize(targetSize, scale: 3)
        view.frame = CGRect(origin: CGPoint(x: -targetSize.width * 2, y: 0), size: targetSize)
        view.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        return UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
    }
}
