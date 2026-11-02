import Foundation
import SwiftUI
import FlareAppleCore
@preconcurrency import KotlinSharedUI
import Textual

struct AgentMarkdownText: View {
    let markdown: String

    var body: some View {
        StructuredText(markdown: markdown)
            .textual.imageAttachmentLoader(ProxyMarkdownImageLoader())
            .textual.textSelection(.enabled)
            .id(ProxyMediaImages.shared.revision)
    }
}

struct ProxyMarkdownImageLoader: AttachmentLoader {
    nonisolated func attachment(
        for url: URL, text: String, environment: ColorEnvironmentValues
    ) async throws -> some Textual.Attachment {
        try await markdownImageAttachments.attachment(for: url, text: text, environment: environment)
    }
}

@MainActor
private let markdownImageAttachments = MarkdownImageAttachments(decoder: .image())

@MainActor
private final class MarkdownImageAttachments<Decoder: AttachmentLoader> {
    private let decoder: Decoder
    private let cache = NSCache<Key, Value>()
    private var pending: [Key: Task<Decoder.Attachment, Error>] = [:]

    init(decoder: Decoder) { self.decoder = decoder }

    func attachment(for url: URL, text: String, environment: ColorEnvironmentValues) async throws -> Decoder.Attachment {
        let scheme = url.scheme?.lowercased()
        guard scheme == "http" || scheme == "https" else {
            guard url.isFileURL || scheme == "data" else { throw URLError(.unsupportedURL) }
            return try await decoder.attachment(for: url, text: text, environment: environment)
        }
        let route = try await ProxyNetwork.shared.snapshot(ref: ProxyRef.Global.shared)
        let key = Key([route.key, url.absoluteString, text])
        if let value = cache.object(forKey: key) { return value.attachment }
        if let task = pending[key] { return try await task.value }

        let task = Task {
            let media = UiMediaImage(url: url.absoluteString, proxy: ProxyRef.Global.shared)
            let (file, _) = try await MediaTransfer.download(media)
            defer { try? FileManager.default.removeItem(at: file) }
            // Decode locally with Textual to retain its animation, sizing, and copy/paste behavior.
            return try await self.decoder.attachment(for: file, text: text, environment: environment)
        }
        pending[key] = task
        defer { pending[key] = nil }
        let attachment = try await task.value
        cache.setObject(Value(attachment), forKey: key)
        return attachment
    }

    private final class Key: NSObject {
        nonisolated let parts: [String]
        nonisolated init(_ parts: [String]) { self.parts = parts }
        nonisolated override var hash: Int { parts.hashValue }
        nonisolated override func isEqual(_ object: Any?) -> Bool { (object as? Key)?.parts == parts }
    }

    private final class Value {
        let attachment: Decoder.Attachment
        init(_ attachment: Decoder.Attachment) { self.attachment = attachment }
    }
}
