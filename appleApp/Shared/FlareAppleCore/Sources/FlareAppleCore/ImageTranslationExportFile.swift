import Foundation

public enum ImageTranslationExportFile {
    public static func isOwned(_ source: URL) -> Bool {
        guard source.isFileURL else { return false }
        let path = source.resolvingSymlinksInPath().path
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        let roots = [
            caches?.appendingPathComponent(Bundle.main.bundleIdentifier ?? "dev.dimension.flare", isDirectory: true)
                .appendingPathComponent("image_translation_sessions", isDirectory: true),
            FileManager.default.temporaryDirectory.appendingPathComponent("translated-exports", isDirectory: true)
        ].compactMap { $0 }
        return roots.contains { path.hasPrefix($0.resolvingSymlinksInPath().path + "/") }
    }

    public static func prepare(_ source: URL) throws -> URL {
        guard isOwned(source) else { throw URLError(.unsupportedURL) }
        let exports = FileManager.default.temporaryDirectory.appendingPathComponent("translated-exports", isDirectory: true)
        if let old = try? FileManager.default.contentsOfDirectory(at: exports, includingPropertiesForKeys: [.contentModificationDateKey]) {
            for file in old {
                if let date = try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate,
                   date.timeIntervalSinceNow < -86400 { try? FileManager.default.removeItem(at: file) }
            }
        }
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("translated-exports", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent(source.lastPathComponent)
        do {
            try FileManager.default.linkItem(at: source, to: file)
        } catch {
            do { try FileManager.default.copyItem(at: source, to: file) }
            catch { try? FileManager.default.removeItem(at: directory); throw error }
        }
        return file
    }
}
