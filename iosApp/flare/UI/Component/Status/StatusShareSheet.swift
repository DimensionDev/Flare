import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import UniformTypeIdentifiers

struct StatusShareSheet: View {
    let statusKey: MicroBlogKey
    let accountType: AccountType
    let shareUrl: String
    let fxShareUrl: String?
    let fixvxShareUrl: String?
    
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appearanceSettings) private var appearanceSettings
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
                    ShareLink(item: .init(string: shareUrl)!) {
                        Label("share_link", systemImage: "link")
                    }
                    
                    if let image {
                        ShareLink(item: Image(uiImage: image), preview: SharePreview("share_screenshot", image: Image(uiImage: image))) {
                            Label("share_screenshot", systemImage: "photo")
                        }
                    }

                    Button {
                        Task {
                            await saveImage(data: data)
                        }
                    } label: {
                        Label("save_screenshot", systemImage: "arrow.down.circle")
                    }
                    
                    if let fxShareUrl = fxShareUrl {
                        ShareLink(item: .init(string: fxShareUrl)!) {
                            Label("share_via_fxembed", systemImage: "link")
                        }
                    }
                    
                    if let fixvxShareUrl = fixvxShareUrl {
                        ShareLink(item: .init(string: fixvxShareUrl)!) {
                            Label("share_via_fixvx", systemImage: "link")
                        }
                    }
                }
                
                Picker("Theme", selection: $theme) {
                    Text("System").tag(nil as ColorScheme?)
                    Text("Light").tag(Optional(ColorScheme.light))
                    Text("Dark").tag(Optional(ColorScheme.dark))
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
                        Image("fa-xmark")
                    }
                }
            }
        }
        .navigationTitle("status_menu_share")
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
    private func previewView(data: UiTimeline) -> some View {
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
            .environment(\.appearanceSettings, appearanceSettings.copy(videoAutoplay: .never))
    }
    
    
    private func renderImage(data: UiTimeline) async -> UIImage? {
        return await previewView(data: data).snapshot()
    }
    
    private func saveImage(data: UiTimeline) async {
        guard let image = image else { return }
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        dismiss()
    }
}

extension View {
    @MainActor
    func snapshot(delay: TimeInterval = 0.1) async -> UIImage? {
        let controller = UIHostingController(rootView: self.edgesIgnoringSafeArea(.top))
        let view = controller.view
        let targetSize = controller.view.intrinsicContentSize
        view?.bounds = CGRect(origin: CGPoint(x: 0, y: 0), size: targetSize)
        view?.backgroundColor = .clear
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))

        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { _ in
            view?.drawHierarchy(in: controller.view.bounds, afterScreenUpdates: true)
        }
    }
}
