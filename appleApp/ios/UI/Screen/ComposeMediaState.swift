import SwiftUI
import PhotosUI
import CoreTransferable
import UniformTypeIdentifiers
import ImageIO
import FlareAppleCore
import KotlinSharedUI

@Observable
@MainActor
class MediaViewModel {
    private struct Slot {
        let id = UUID()
        var item: MediaItem?
    }

    var selectedItems: [PhotosPickerItem] = []
    private var slots: [Slot] = []
    var items: [MediaItem] { slots.compactMap(\.item) }
    var sensitive = false
    var maxSize = 4
    var enableAltText = true
    var altTextMaxLength = 500
    var imagesAllowed: () -> Bool = { true }
    var onError: (Error) -> Void = { _ in }
    private var pending: Set<UUID> = []
    private var progress: [UUID: Progress] = [:]
    private var textOrder: [UUID] = []
    private var textResults: [UUID: (String, @MainActor (String) -> Void)] = [:]
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var importError: Error?
    private var submission: Task<Void, Never>?
    private var submitting = false
    private var generation = 0
    var hasPendingImports: Bool { !pending.isEmpty }

    func update() {
        slots.removeAll { slot in
            if let pickerItem = slot.item?.item { return !selectedItems.contains(pickerItem) }
            return false
        }
        let additions = selectedItems.filter { selection in !items.contains { $0.item == selection } }
        slots += additions.prefix(max(0, maxSize - slots.count)).map { Slot(item: MediaItem(item: $0)) }
        selectedItems = items.compactMap(\.item)
    }

    func importImage(_ provider: NSItemProvider) {
        guard imagesAllowed(), slots.count < maxSize else { return }
        if pending.isEmpty { importError = nil }
        let slot = Slot()
        slots.append(slot)
        let id = slot.id
        pending.insert(id)
        progress[id] = ComposeImageInput.loadImage(provider) { result in
            Task { @MainActor in
                guard self.pending.contains(id), let index = self.slots.firstIndex(where: { $0.id == id }) else { return }
                defer { self.finish(id) }
                guard self.imagesAllowed(), index < self.maxSize else {
                    self.slots.remove(at: index)
                    return
                }
                switch result {
                case .success(let file):
                    self.slots[index].item = MediaItem(file: file)
                case .failure(let error):
                    self.slots.remove(at: index)
                    self.importError = error
                    self.onError(error)
                }
            }
        }
    }

    func importText(_ provider: NSItemProvider, insert: @escaping @MainActor (String) -> Void) {
        guard provider.canLoadObject(ofClass: NSString.self) else { return }
        let id = UUID()
        textOrder.append(id)
        pending.insert(id)
        progress[id] = provider.loadObject(ofClass: NSString.self) { value, error in
            let text = value as? String
            Task { @MainActor in
                guard self.pending.contains(id) else { return }
                if text == nil {
                    let error = error ?? CocoaError(.fileReadUnknown)
                    self.importError = error
                    self.onError(error)
                }
                self.textResults[id] = (text ?? "", insert)
                while let first = self.textOrder.first, let result = self.textResults.removeValue(forKey: first) {
                    if !result.0.isEmpty { result.1(result.0) }
                    self.textOrder.removeFirst()
                    self.finish(first)
                }
            }
        }
    }

    func importDrop(_ providers: [NSItemProvider], insert: @escaping @MainActor (String) -> Void) {
        // Reserve images first so independent text cannot prevent a new image batch from clearing an old error.
        for provider in providers where ComposeImageInput.isImage(provider) {
            importImage(provider)
        }
        for provider in providers where !ComposeImageInput.isImage(provider) {
            importText(provider, insert: insert)
        }
    }

    func dispatchWhenReady(
        _ dispatch: @escaping (@escaping (Bool) -> Void) -> Void,
        completion: @escaping (Bool) -> Void
    ) {
        guard !submitting else { return }
        submitting = true
        let generation = self.generation
        submission = Task {
            while !pending.isEmpty {
                await withCheckedContinuation { waiters.append($0) }
            }
            guard !Task.isCancelled, generation == self.generation else { return }
            guard importError == nil, items.allSatisfy(\.isReady) else {
                submitting = false
                completion(false)
                return
            }
            let files = items.compactMap(\.file)
            dispatch { success in
                defer { withExtendedLifetime(files) {} }
                guard generation == self.generation else { return }
                self.submitting = false
                completion(success)
            }
        }
    }

    func cancelImports() {
        generation += 1
        submission?.cancel()
        submission = nil
        progress.values.forEach { $0.cancel() }
        progress.removeAll()
        textOrder.removeAll()
        textResults.removeAll()
        pending.removeAll()
        slots.removeAll { $0.item == nil }
        importError = nil
        submitting = false
        resumeWaiters()
    }

    private func finish(_ id: UUID) {
        pending.remove(id)
        progress.removeValue(forKey: id)
        if pending.isEmpty { resumeWaiters() }
    }

    private func resumeWaiters() {
        let continuations = waiters
        waiters = []
        continuations.forEach { $0.resume() }
    }

    func restore(draftMedias: [UiDraftMedia]) {
        cancelImports()
        selectedItems = []
        slots = draftMedias.compactMap(MediaItem.init(draftMedia:)).map { Slot(item: $0) }
    }

    func setInitialImage(data: Data, fileName: String) {
        cancelImports()
        selectedItems = []
        slots = MediaItem(data: data, fileName: fileName).map { [Slot(item: $0)] } ?? []
    }

    func remove(item: MediaItem) {
        slots.removeAll { $0.item == item }
        if let pickerItem = item.item { selectedItems.removeAll { $0 == pickerItem } }
        if slots.isEmpty { sensitive = false }
    }
}

private nonisolated struct PickedComposeFile: Transferable {
    let file: ComposeMediaFile

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { received in
            PickedComposeFile(file: try ComposeMediaFile(copying: received.file))
        }
        FileRepresentation(importedContentType: .image) { received in
            PickedComposeFile(file: try ComposeMediaFile(copying: received.file))
        }
    }
}

@Observable
class MediaItem: Equatable, Identifiable {
    static func == (lhs: MediaItem, rhs: MediaItem) -> Bool {
        lhs.id == rhs.id
    }
    let item: PhotosPickerItem?
    var image: UIImage?
    var data: Data?
    var file: ComposeMediaFile?
    var mimeType: String?
    var loadError: String?
    var isReady: Bool { file != nil || data != nil }
    var altText: String = ""
    let id: String
    var fileName: String
    var type: FileType = .other

    init(item: PhotosPickerItem) {
        self.item = item
        self.id = item.itemIdentifier ?? UUID().uuidString
        self.fileName = item.itemIdentifier ?? UUID().uuidString

        if let contentType = item.supportedContentTypes.first {
            if contentType.conforms(to: .image) {
                self.type = .image
            } else if contentType.conforms(to: .movie) {
                self.type = .video
            }
        }

        self.mimeType = item.supportedContentTypes.first?.preferredMIMEType
        item.loadTransferable(type: PickedComposeFile.self) { result in
            DispatchQueue.main.async {
                do {
                    guard let picked = try result.get() else {
                        self.loadError = "Unable to load this media file"
                        return
                    }
                    self.file = picked.file
                    self.fileName = picked.file.url.lastPathComponent
                    if self.type == .image {
                        self.image = UIImage(contentsOfFile: picked.file.url.path)
                    }
                } catch {
                    self.loadError = error.localizedDescription
                }
            }
        }
    }

    init(file: ComposeMediaFile) {
        self.item = nil
        self.id = UUID().uuidString
        self.file = file
        self.image = UIImage(contentsOfFile: file.url.path)
        self.type = .image
        let source = CGImageSourceCreateWithURL(file.url as CFURL, nil)
        let contentType = source.flatMap { CGImageSourceGetType($0) }.flatMap { UTType($0 as String) }
        self.mimeType = contentType?.preferredMIMEType
        self.fileName = file.url.deletingPathExtension().lastPathComponent + "." + (contentType?.preferredFilenameExtension ?? "img")
    }

    init?(data: Data, fileName: String) {
        guard let image = UIImage(data: data) else { return nil }
        self.item = nil
        self.id = UUID().uuidString
        self.fileName = fileName
        self.data = data
        self.image = image
        self.type = .image
    }

    init?(draftMedia: UiDraftMedia) {
        let fileURL = URL(fileURLWithPath: draftMedia.cachePath)
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }

        self.item = nil
        self.id = draftMedia.cachePath
        self.fileName = draftMedia.fileName ?? fileURL.lastPathComponent
        self.file = ComposeMediaFile(draftURL: fileURL)
        self.altText = draftMedia.altText ?? ""

        switch draftMedia.type {
        case .image:
            self.type = .image
            self.image = UIImage(contentsOfFile: fileURL.path)
        case .video:
            self.type = .video
        default:
            self.type = .other
        }
    }
}
