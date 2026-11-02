import UIKit
import UniformTypeIdentifiers
import ImageIO
import FlareAppleCore

/// UITextView owns the text/drop position; images are consumed before its text fallback runs.
final class ComposeImageInput: NSObject, UITextPasteDelegate {
    var receiveImage: ((NSItemProvider) -> Void)?

    func install(on textView: UITextView) {
        let configuration = UIPasteConfiguration(forAccepting: NSString.self)
        configuration.addAcceptableTypeIdentifiers([UTType.image.identifier])
        textView.pasteConfiguration = configuration
        textView.pasteDelegate = self
    }

    func textPasteConfigurationSupporting(
        _ textPasteConfigurationSupporting: any UITextPasteConfigurationSupporting,
        transform item: any UITextPasteItem
    ) {
        if Self.isImage(item.itemProvider) {
            receiveImage?(item.itemProvider)
            item.setNoResult()
        } else {
            item.setDefaultResult()
        }
    }

    static func isImage(_ provider: NSItemProvider) -> Bool {
        provider.hasItemConformingToTypeIdentifier(UTType.image.identifier)
    }

    nonisolated static func loadImage(
        _ provider: NSItemProvider,
        completion: @escaping @Sendable (Result<ComposeMediaFile, Error>) -> Void
    ) -> Progress {
        let types = provider.registeredTypeIdentifiers.filter { UTType($0)?.conforms(to: .image) == true }
        let type = types.first { UTType($0) == .gif } ?? types.first ?? UTType.image.identifier
        // Start loading inside the system paste/drop callback, and copy before its URL expires.
        return provider.loadFileRepresentation(forTypeIdentifier: type) { url, error in
            completion(Result {
                guard let url else { throw error ?? CocoaError(.fileReadUnknown) }
                let file = try ComposeMediaFile(copying: url)
                guard let image = CGImageSourceCreateWithURL(file.url as CFURL, nil),
                      CGImageSourceGetCount(image) > 0,
                      CGImageSourceGetStatus(image) == .statusComplete else {
                    throw CocoaError(.fileReadCorruptFile)
                }
                return file
            })
        }
    }
}
