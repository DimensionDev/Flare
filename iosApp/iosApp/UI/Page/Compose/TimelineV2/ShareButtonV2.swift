import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import SwiftDate
import SwiftUI
import UIKit

#if canImport(_Translation_SwiftUI)
    import Translation
#endif

struct ShareButtonV2: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(\.appSettings) var appSettings
    @Environment(\.openURL) private var openURL
    @Environment(FlareRouter.self) var router
    @Environment(FlareTheme.self) private var theme

    @State private var isShareAsImageSheetPresented: Bool = false
    @State private var showTextForSelection: Bool = false
    @State private var renderer: ImageRenderer<AnyView>?
    @State private var capturedImage: UIImage?
    @State private var isPreparingShare: Bool = false
    @State private var showTranslation: Bool = false
    @State private var showSelectUrlSheet: Bool = false

    // ✅ 修改：使用Swift原生类型替代shared类型
    let item: TimelineItem // 使用TimelineItem替代UiTimelineItemContentStatus
    let view: TimelineStatusViewV2 // 使用TimelineStatusViewV2替代TimelineStatusView

    private var statusUrl: URL? {
        // ✅ 修改：从TimelineItem获取URL
        guard !item.url.isEmpty else { return nil }
        return URL(string: item.url)
    }

    private func prepareScreenshot(completion: @escaping (UIImage?) -> Void) {
        // ✅ 修改：使用TimelineStatusViewV2的截图包装器
        let captureView = StatusCaptureWrapperV2(content: view)
            .environment(\.appSettings, appSettings)
            .environment(\.colorScheme, colorScheme)
            .environment(\.isInCaptureMode, true)
            .environment(router)
            .environment(theme).applyTheme(theme)

        let controller = UIHostingController(rootView: captureView)

        let targetSize = controller.sizeThatFits(in: CGSize(
            width: UIScreen.main.bounds.width - 24,
            height: UIView.layoutFittingExpandedSize.height
        ))

        controller.view.bounds = CGRect(origin: .zero, size: targetSize)
        controller.view.backgroundColor = .clear

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let renderer = UIGraphicsImageRenderer(size: targetSize)
            let image = renderer.image { _ in
                controller.view.drawHierarchy(in: controller.view.bounds, afterScreenUpdates: true)
            }
            completion(image)
        }
    }

    private func getShareTitle(allContent: Bool) -> String {
        // ✅ 修改：从TimelineItem获取分享标题
        let content = item.content.raw
        let author = item.user?.name.raw ?? item.user?.handle ?? "Unknown"

        if allContent {
            return "\(author): \(content)"
        } else {
            return content
        }
    }

    var body: some View {
        Menu {
            Button(action: {
                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Report Success", comment: "")).show()
            }) {
                Label("Report", systemImage: "exclamationmark.triangle")
            }

            Button(action: {
                // ✅ 修改：从TimelineItem获取文本内容
                UIPasteboard.general.string = item.content.raw
                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
            }) {
                Label("Copy Text ", systemImage: "doc.on.doc")
            }

            Button(action: {
                // ✅ 修改：从TimelineItem获取Markdown内容
                UIPasteboard.general.string = item.content.markdown
                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
            }) {
                Label("Copy Text (MarkDown)", systemImage: "doc.on.doc")
            }

            if !item.images.isEmpty {
                Button(action: {
                    showSelectUrlSheet = true
                }) {
                    Label("Copy Media Link", systemImage: "photo.on.rectangle")
                }
            }

            Button(action: {
                showTextForSelection = true
            }) {
                Label("Copy Any", systemImage: "text.cursor")
            }
            .buttonStyle(PlainButtonStyle())

            if let url = statusUrl {
                Button(action: {
                    UIPasteboard.general.string = url.absoluteString
                    ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
                }) {
                    Label("Copy Tweet Link", systemImage: "link")
                }

                Button(action: {
                    openURL(url)
                }) {
                    Label("Open in Browser", systemImage: "safari")
                }
            }

            Divider()

            Menu {
                Button(action: {
                    isPreparingShare = true
                    prepareScreenshot { image in
                        if let image {
                            var activityItems: [Any] = []
                            let shareTitle = getShareTitle(allContent: true)
                            activityItems.append(shareTitle)
                            activityItems.append(image)

                            if let url = statusUrl {
                                activityItems.append(url)
                            }

                            let activityVC = UIActivityViewController(
                                activityItems: activityItems,
                                applicationActivities: nil
                            )

                            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                               let window = windowScene.windows.first,
                               let rootVC = window.rootViewController
                            {
                                activityVC.popoverPresentationController?.sourceView = window
                                rootVC.present(activityVC, animated: true)
                            }
                        }
                        isPreparingShare = false
                    }
                }) {
                    if isPreparingShare {
                        Label("Preparing...", systemImage: "hourglass")
                    } else {
                        Label("Share Post", systemImage: "square.and.arrow.up")
                    }
                }
                .disabled(isPreparingShare)

                Button(action: {
                    prepareScreenshot { image in
                        if let image {
                            capturedImage = image
                            let newRenderer = ImageRenderer(content: AnyView(
                                StatusCaptureWrapperV2(content: view)
                                    .environment(\.appSettings, appSettings)
                                    .environment(\.colorScheme, colorScheme)
                                    .environment(\.isInCaptureMode, true)
                                    .environment(theme).applyTheme(theme)
                                    .environment(router)
                            ))
                            newRenderer.scale = 3.0
                            newRenderer.isOpaque = true
                            renderer = newRenderer
                            isShareAsImageSheetPresented = true
                        }
                    }
                }) {
                    Label("Share as Image", systemImage: "camera")
                }
            } label: {
                Label("Share", systemImage: "square.and.arrow.up")
            }

            #if canImport(_Translation_SwiftUI)
                Button(action: {
                    showTranslation = true
                }) {
                    Label("System Translate", systemImage: "character.bubble")
                }
            #endif

            Divider()

            Button(action: {
                FlareLog.debug("ShareButtonV2 Save Media tapped")
                ToastView(
                    icon: UIImage(systemName: "arrow.down.to.line"),
                    message: String(localized: "download to App \n Download Manager")
                ).show()
            }) {
                Label("Save Media", systemImage: "arrow.down.to.line")
            }

            if !item.images.isEmpty {
                Button(action: {
                    showSelectUrlSheet = true
                }) {
                    Label("Copy Media URLs", systemImage: "link")
                }
            }
        } label: {
            HStack {
                Spacer()
                Image(systemName: "square.and.arrow.up")
                    .renderingMode(.template)
                    // .foregroundColor(theme.labelColor)
                    .font(.system(size: 16))
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        #if canImport(_Translation_SwiftUI)
        .addTranslateView(isPresented: $showTranslation, text: item.content.raw)
        #endif
        .sheet(isPresented: $isShareAsImageSheetPresented) {
            if let renderer {
                StatusShareAsImageViewV2(
                    content: view,
                    renderer: renderer,
                    shareText: getShareTitle(allContent: false)
                )
                .environment(\.appSettings, appSettings)
                .environment(\.colorScheme, colorScheme)
                .environment(\.isInCaptureMode, true)
                .environment(router)
                .environment(theme).applyTheme(theme)
            }
        }
        .sheet(isPresented: $showTextForSelection) {
            let imageURLsString = item.images.map(\.url).joined(separator: "\n")
            let selectableContent = AttributedString(item.content.markdown + "\n" + imageURLsString)

            StatusRowSelectableTextView(content: selectableContent)
                .tint(.accentColor)
        }
        .sheet(isPresented: $showSelectUrlSheet) {
            let urlsString = item.images.map(\.url).joined(separator: "\n")
            StatusRowSelectableTextView(content: AttributedString(urlsString))
                .tint(.accentColor)
        }
    }
}

struct StatusCaptureWrapperV2: View {
    let content: TimelineStatusViewV2

    var body: some View {
        content
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
    }
}
