import SwiftUI
import UniformTypeIdentifiers

public extension UTType {
    nonisolated static var opml: UTType {
        UTType(exportedAs: "dev.dimension.flare.opml", conformingTo: .xml)
    }
}

public struct OPMLFile: FileDocument {
    public nonisolated static var readableContentTypes: [UTType] { [.opml] }

    public var text = ""

    public init(initialText: String = "") {
        text = initialText
    }

    public init(configuration: ReadConfiguration) throws {
        if let data = configuration.file.regularFileContents {
            text = String(decoding: data, as: UTF8.self)
        }
    }

    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let data = Data(text.utf8)
        return FileWrapper(regularFileWithContents: data)
    }
}

public struct TextDocument: FileDocument {
    public var text: String

    public static var readableContentTypes: [UTType] { [.plainText] }

    public init(text: String = "") {
        self.text = text
    }

    public init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let string = String(data: data, encoding: .utf8)
        else {
            throw CocoaError(.fileReadCorruptFile)
        }
        text = string
    }

    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let data = Data(text.utf8)
        return FileWrapper(regularFileWithContents: data)
    }
}
