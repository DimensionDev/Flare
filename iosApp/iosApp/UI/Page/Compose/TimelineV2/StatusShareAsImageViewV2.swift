import Generated
import SwiftUI
import UIKit
import UniformTypeIdentifiers

@available(iOS 16.0, *)
struct TransferableImageV2: Transferable {
    let image: UIImage

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .png) { transferableImage in
            guard let data = transferableImage.image.pngData() else {
                throw URLError(.badServerResponse)
            }
            return data
        }
    }
}

class ScreenshotRendererV2 {
    static func render(_ view: some View) -> UIImage? {
        let controller = UIHostingController(rootView: view)
        let targetSize = controller.view.sizeThatFits(CGSize(
            width: UIScreen.main.bounds.width - 24,
            height: UIView.layoutFittingExpandedSize.height
        ))

        controller.view.bounds = CGRect(origin: .zero, size: targetSize)
        controller.view.backgroundColor = .clear

        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            controller.view.drawHierarchy(in: controller.view.bounds, afterScreenUpdates: true)
        }
    }
}

struct StatusShareAsImageViewV2: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    let content: TimelineStatusViewV2 // ✅ 修改：使用TimelineStatusViewV2
    let renderer: ImageRenderer<AnyView>
    @State private var capturedImage: UIImage?
    @State private var isImageReady: Bool = false
    let shareText: String

    var rendererImage: Image {
        if let image = capturedImage {
            return Image(uiImage: image)
        }
        return Image(uiImage: renderer.uiImage ?? UIImage())
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if isImageReady {
                        rendererImage
                            .resizable()
                            .scaledToFit()
                    } else {
                        ProgressView()
                            .frame(maxWidth: .infinity, minHeight: 200)
                    }
                }
                .listRowBackground(colorScheme == .dark ? Color.black : Color.white)

                Section {
                    if let image = capturedImage {
                        ShareLink(
                            item: TransferableImageV2(image: image),
                            subject: Text(shareText),
                            message: Text(shareText),
                            preview: SharePreview(
                                shareText,
                                image: rendererImage
                            )
                        ) {
                            Label("Share", systemImage: "square.and.arrow.up")
                                .foregroundColor(theme.tintColor)
                        }
                        .disabled(!isImageReady)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Text("Done")
                            .bold()
                    }
                }
            }
            .navigationTitle("Share post as image")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationBackground(.ultraThinMaterial)
        .presentationCornerRadius(16)
        .onAppear {
            let view = StatusCaptureWrapperV2(content: content)
                .environment(\.appSettings, appSettings)
                .environment(\.colorScheme, colorScheme)
                .environment(\.isInCaptureMode, true)
                .environment(router)
                .environment(theme).applyTheme(theme)

            // 增加延迟时间，确保敏感内容和媒体完全加载后再截图
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                if let image = ScreenshotRendererV2.render(view) {
                    capturedImage = image
                    isImageReady = true
                }
            }
        }
        .environment(theme).applyTheme(theme)
    }
}
