import Foundation
import UniformTypeIdentifiers

/// A non-UI Safari Action extension that returns a custom URL to the page's
/// JavaScript `finalize` hook. Safari then opens that URL as a user-initiated
/// navigation, allowing the containing Flare app to receive the webpage URL.
final class ActionRequestHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        guard let provider = context.inputItems
            .compactMap({ $0 as? NSExtensionItem })
            .flatMap({ $0.attachments ?? [] })
            .first(where: { $0.hasItemConformingToTypeIdentifier(UTType.propertyList.identifier) })
        else {
            Self.cancel(context, reason: "Safari did not provide webpage information.")
            return
        }
        let contextBox = ExtensionContextBox(context)

        provider.loadItem(
            forTypeIdentifier: UTType.propertyList.identifier,
            options: nil
        ) { item, error in
            guard error == nil,
                  let dictionary = item as? [String: Any],
                  let results = dictionary[NSExtensionJavaScriptPreprocessingResultsKey] as? [String: Any],
                  let source = results["url"] as? String,
                  let sourceURL = URL(string: source),
                  let scheme = sourceURL.scheme?.lowercased(),
                  scheme == "https" || scheme == "http",
                  let flareURL = Self.flareURL(for: source)
            else {
                Self.cancel(contextBox.value, reason: "The current webpage URL is unavailable.")
                return
            }

            let output = NSExtensionItem()
            let finalizeArguments = [
                NSExtensionJavaScriptFinalizeArgumentKey: ["flareURL": flareURL.absoluteString]
            ] as NSDictionary
            output.attachments = [
                NSItemProvider(
                    item: finalizeArguments,
                    typeIdentifier: UTType.propertyList.identifier
                )
            ]

            contextBox.value.completeRequest(returningItems: [output])
        }
    }

    private static func flareURL(for source: String) -> URL? {
        var components = URLComponents()
        components.scheme = "flare"
        components.host = "open"
        components.queryItems = [URLQueryItem(name: "url", value: source)]
        return components.url
    }

    private static func cancel(_ context: NSExtensionContext, reason: String) {
        context.cancelRequest(
            withError: NSError(
                domain: "dev.dimension.flare.open-in-flare",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: reason]
            )
        )
    }
}

/// Foundation's extension context is callback-safe but does not currently carry
/// a Sendable annotation in the SDK overlay.
private final class ExtensionContextBox: @unchecked Sendable {
    let value: NSExtensionContext

    init(_ value: NSExtensionContext) {
        self.value = value
    }
}
