import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
import SwiftDate
import SwiftUI
import UIKit

#if canImport(_Translation_SwiftUI)
    import Translation
#endif

#if canImport(_Translation_SwiftUI)
    extension View {
        func addTranslateView(isPresented: Binding<Bool>, text: String) -> some View {
            if #available(iOS 17, *) {
                return self.translationPresentation(isPresented: isPresented, text: text)
            } else {
                return self
            }
        }
    }
#endif

struct ShareButton: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(\.appSettings) var appSettings
    @Environment(\.openURL) private var openURL
    @EnvironmentObject var router: FlareRouter
    @Environment(FlareTheme.self) private var theme

    @State private var isShareAsImageSheetPresented: Bool = false
    @State private var showTextForSelection: Bool = false
    @State private var renderer: ImageRenderer<AnyView>?
    @State private var capturedImage: UIImage?
    @State private var isPreparingShare: Bool = false
    @State private var showTranslation: Bool = false
    // @State private var showReportAlert = false
    @State private var showSelectUrlSheet: Bool = false

    let content: UiTimelineItemContentStatus
    let view: TimelineStatusView

    private var statusUrl: URL? {
        guard let urlString = content.url as String? else { return nil }
        return URL(string: urlString)
    }

    private func prepareScreenshot(completion: @escaping (UIImage?) -> Void) {
        let captureView = StatusCaptureWrapper(content: view)
            .environment(\.appSettings, appSettings)
            .environment(\.colorScheme, colorScheme)
            .environment(\.isInCaptureMode, true)
            .environmentObject(router)
            .environment(theme).applyTheme(theme)

        let controller = UIHostingController(rootView: captureView)

        let targetSize = controller.sizeThatFits(in: CGSize(
            width: UIScreen.main.bounds.width - 24,
            height: UIView.layoutFittingExpandedSize.height
        ))

        controller.view.frame = CGRect(origin: .zero, size: targetSize)
        controller.view.backgroundColor = .clear

        controller.view.layoutIfNeeded()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            let image = ScreenshotRenderer.render(captureView)
            completion(image)
        }
    }

    private func getShareTitle(allContent: Bool) -> String {
        if allContent {
            return content.content.raw
        }
        let maxLength = 100
        if content.content.raw.count > maxLength {
            let index = content.content.raw.index(content.content.raw.startIndex, offsetBy: maxLength)
            return String(content.content.raw[..<index]) + "..."
        }
        return content.content.raw
    }

    var body: some View {
        Menu {
            Button(action: {
                //  showReportAlert = true
                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Report Success", comment: "")).show()
            }) {
                Label("Report", systemImage: "exclamationmark.triangle")
            }

            Button(action: {
                UIPasteboard.general.string = content.content.raw
                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
            }) {
                Label("Copy Text ", systemImage: "doc.on.doc")
            }

            Button(action: {
                UIPasteboard.general.string = content.content.markdown
                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
            }) {
                Label("Copy Text (MarkDown)", systemImage: "doc.on.doc")
            }

            if !content.images.isEmpty {
                Button(action: {
                    let extractedUrls = content.images.compactMap(\.url)
                    if !extractedUrls.isEmpty {
                        showSelectUrlSheet = true
                    } else if let url = statusUrl {
                        UIPasteboard.general.string = url.absoluteString
                    }
                }) {
                    Label("Copy Media Link", systemImage: "photo.on.rectangle")
                }
            }

            Button(action: {
                showTextForSelection = true

            }) {
                Label("Copy Any", systemImage: "text.cursor")
            }.buttonStyle(PlainButtonStyle())

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
//                      DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    prepareScreenshot {
                        image in
                        if let image {
                            capturedImage = image
                            let newRenderer = ImageRenderer(content: AnyView(
                                StatusCaptureWrapper(content: view)
                                    .environment(\.appSettings, appSettings)
                                    .environment(\.colorScheme, colorScheme)
                                    .environment(\.isInCaptureMode, true)
                                    .environment(theme).applyTheme(theme)
                                    .environmentObject(router)
                            ))
                            newRenderer.scale = 3.0
                            newRenderer.isOpaque = true
                            renderer = newRenderer
                            isShareAsImageSheetPresented = true
                        }
                    }
//                    }
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
                    Label("Apple Translate UI", systemImage: "character.bubble")
                }
            #endif

            Divider()

            Button(action: {
                print("Save Media tapped")

                ToastView(
                    icon: UIImage(systemName: "square.and.arrow.down"),
                    message: String(localized: "download to App \n Download Manager")
                ).show()

                for media in content.images {
                    if let image = media as? UiMediaImage {
                        var imageUrl = image.url

                        if content.card?.url == imageUrl {
                            // article image
                            imageUrl = image.previewUrl
                        }
                        print("Starting download for Image: \(imageUrl)")
                        DownloadHelper.shared.startMediaDownload(
                            url: imageUrl,
                            mediaType: .image,
                            previewImageUrl: imageUrl
                        )
                    } else if let video = media as? UiMediaVideo {
                        let videoUrl = video.url
                        print("Starting download for Video: \(videoUrl)")

                        if videoUrl.contains(".mp4") {
                            DownloadHelper.shared.startMediaDownload(
                                url: videoUrl,
                                mediaType: .video,
                                previewImageUrl: video.thumbnailUrl
                            )
                        } else {
                            ToastView(
                                icon: UIImage(systemName: "flag.fill"),
                                message: String(localized: "only support mp4")
                            ).show()
                        }

                    } else if let gif = media as? UiMediaGif {
                        let gifUrl = gif.url
                        print("Starting download for GIF: \(gifUrl)")
                        DownloadHelper.shared.startMediaDownload(
                            url: gifUrl,
                            mediaType: .gif,
                            previewImageUrl: gif.previewUrl
                        )
                    } else if let audio = media as? UiMediaAudio {
                        let audioUrl = audio.url
                        print("Starting download for Audio: \(audioUrl)")
                        DownloadHelper.shared.startMediaDownload(
                            url: audioUrl,
                            mediaType: .audio
                        )
                    }
                }
            }) {
                Label("Save Media", systemImage: "arrow.down.to.line")
            }

        } label: {
            HStack {
                Spacer()
                Label("", systemImage: "square.and.arrow.up")
                    .imageScale(.medium)
                    .foregroundColor(theme.labelColor)
                    .font(.system(size: 13))
                // .foregroundColor(theme.tintColor)
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .sheet(isPresented: $showTextForSelection) {
            let imageURLsString = content.images.compactMap(\.url).joined(separator: "\n")
            let selectableContent = AttributedString(content.content.markdown + "\n" + imageURLsString)

            StatusRowSelectableTextView(content: selectableContent)
                .tint(.accentColor)
        }
        #if canImport(_Translation_SwiftUI)
        .addTranslateView(isPresented: $showTranslation, text: content.content.raw)
        #endif
        .sheet(isPresented: $isShareAsImageSheetPresented) {
            if let renderer {
                StatusShareAsImageView(
                    content: view,
                    renderer: renderer,
                    shareText: getShareTitle(allContent: false)
                )
                .environment(\.appSettings, appSettings)
                .environment(\.colorScheme, colorScheme)
                .environment(\.isInCaptureMode, true)
                .environmentObject(router)
                .environment(theme).applyTheme(theme)
            }
        }
        .sheet(isPresented: $showSelectUrlSheet) {
            let urlsString = content.images.compactMap(\.url).joined(separator: "\n")
            StatusRowSelectableTextView(content: AttributedString(urlsString))
                .tint(.accentColor)
        }
    }
}
