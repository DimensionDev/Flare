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

    let content: TimelineStatusViewV2
    let shareText: String

    @State private var capturedImage: UIImage?
    @State private var isGenerating: Bool = true
    @State private var isOptimizing: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if isGenerating {
                        ProgressView("Generating image...")
                            .frame(maxWidth: .infinity, minHeight: 200)
                    } else if let capturedImage {
                        VStack {
                            Image(uiImage: capturedImage)
                                .resizable()
                                .scaledToFit()

                            if isOptimizing {
                                HStack {
                                    ProgressView()
                                        .scaleEffect(0.7)
                                    Text("Optimizing...")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                                .padding(.top, 4)
                            }
                        }
                    }
                }
                .listRowBackground(colorScheme == .dark ? Color.black : Color.white)
            }
            .scrollContentBackground(.hidden)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Text("Done")
                            .bold()
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    if !isGenerating, let capturedImage {
                        ShareLink(
                            item: TransferableImageV2(image: capturedImage),
                            subject: Text(shareText),
                            message: Text(shareText),
                            preview: SharePreview(
                                shareText,
                                image: Image(uiImage: capturedImage)
                            )
                        ) {
                            Image(systemName: "square.and.arrow.up")
                                .foregroundColor(theme.tintColor)
                        }
                    }
                }
            }
            .navigationTitle("Share post as image")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationBackground(.ultraThinMaterial)
        .presentationCornerRadius(16)
        .onAppear {
            generateScreenshot()
        }
        .environment(theme).applyTheme(theme)
    }

    private func generateScreenshot() {
        generateQuickScreenshot()

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            generateOptimizedScreenshot()
        }
    }

    private func generateQuickScreenshot() {
        let captureView = createCaptureView()
        let renderer = ImageRenderer(content: captureView)
        renderer.scale = 2.0
        renderer.isOpaque = true
        renderer.proposedSize = ProposedViewSize(
            width: UIScreen.main.bounds.width - 24,
            height: nil
        )

        if let image = renderer.uiImage {
            capturedImage = image
            isGenerating = false
            isOptimizing = true
        }
    }

    private func generateOptimizedScreenshot() {
        let captureView = createCaptureView()
        let renderer = ImageRenderer(content: captureView)
        renderer.scale = 3.0
        renderer.isOpaque = true
        renderer.proposedSize = ProposedViewSize(
            width: UIScreen.main.bounds.width - 24,
            height: nil
        )

        if let image = renderer.uiImage {
            withAnimation(.easeInOut(duration: 0.3)) {
                capturedImage = image
                isOptimizing = false
            }
        } else {
            isOptimizing = false
        }
    }

    private func createCaptureView() -> some View {
        StatusCaptureWrapperV2(content: content)
            .environment(\.appSettings, appSettings)
            .environment(\.colorScheme, colorScheme)
            .environment(\.isInCaptureMode, true)
            .environment(router)
            .environment(theme).applyTheme(theme)
    }
}
