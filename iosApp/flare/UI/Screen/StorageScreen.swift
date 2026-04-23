import SwiftUI
@preconcurrency import KotlinSharedUI
import Kingfisher
import UniformTypeIdentifiers
import GSPlayer
import VideoPlayer
import Drops
import WebKit

struct StorageScreen: View {
    private let storagePresenter: StoragePresenter
    @StateObject private var presenter: KotlinPresenter<StorageState>
    @StateObject private var exportPresenter = KotlinPresenter(presenter: ExportDataPresenter())
    @State private var showDatabaseClearAlert = false
    @State private var showImageClearAlert = false
    @State private var showFileExporter = false
    @State private var showFileImporter = false
    @State private var showImportConfirmation = false
    @State private var pendingImportJson: String?
    @State private var jsonFile: JSONFile?
    @State private var isClearingImageCache = false
    @State private var isClearingDatabaseCache = false

    init() {
        let storagePresenter = StoragePresenter()
        self.storagePresenter = storagePresenter
        _presenter = StateObject(wrappedValue: KotlinPresenter(presenter: storagePresenter))
    }

    private var isClearingStorage: Bool {
        isClearingImageCache || isClearingDatabaseCache
    }

    var body: some View {
        ZStack {
            List {
                Button(role: .destructive) {
                    guard !isClearingStorage else { return }
                    showImageClearAlert = true
                } label: {
                    Label {
                        Text("storage_clear_image_cache")
                    } icon: {
                        Image("fa-image")
                    }
                }
                .alert("storage_clear_image_cache_confirm", isPresented: $showImageClearAlert) {
                    Button("Cancel", role: .cancel) {
                        showImageClearAlert = false
                    }
                    Button("Ok", role: .destructive) {
                        guard !isClearingStorage else { return }
                        showImageClearAlert = false
                        isClearingImageCache = true
                        KingfisherManager.shared.cache.clearMemoryCache()
                        try? VideoCacheManager.cleanAllCache()
                        VideoPlayer.cleanAllCache()
                        let types: Set<String> = [
                            WKWebsiteDataTypeMemoryCache,
                            WKWebsiteDataTypeDiskCache
                        ]

                        WKWebsiteDataStore.default().removeData(
                            ofTypes: types,
                            modifiedSince: .distantPast
                        ) {
                        }
                        KingfisherManager.shared.cache.clearDiskCache {
                            Task { @MainActor in
                                isClearingImageCache = false
                            }
                        }
                    }
                }
                
                
                Button(role: .destructive) {
                    guard !isClearingStorage else { return }
                    showDatabaseClearAlert = true
                } label: {
                    Label {
                        Text("storage_clear_database_cache\(presenter.state.userCount) \(presenter.state.statusCount)")
                    } icon: {
                        Image("fa-database")
                    }
                }
                .alert("storage_clear_database_cache_confirm", isPresented: $showDatabaseClearAlert) {
                    Button("Cancel", role: .cancel) {
                        showDatabaseClearAlert = false
                    }
                    Button("Ok", role: .destructive) {
                        guard !isClearingStorage else { return }
                        showDatabaseClearAlert = false
                        Task { @MainActor in
                            isClearingDatabaseCache = true
                            defer {
                                isClearingDatabaseCache = false
                            }
                            try? await storagePresenter.clearCacheSuspend()
                        }
                    }
                }
                
                NavigationLink(value: Route.appLog) {
                    Label {
                        Text("storage_view_app_log")
                    } icon: {
                        Image(.faEnvelope)
                    }
                }
                
                Button {
                    guard !isClearingStorage else { return }
                    Task {
                        do {
                            let json = try await exportPresenter.state.export()
                            self.jsonFile = JSONFile(text: json)
                            self.showFileExporter = true
                        } catch {
                            Drops.show(.init(stringLiteral: .init(localized: "export_error")))
                        }
                    }
                } label: {
                    Label {
                        Text("settings_storage_export_data")
                        Text("settings_storage_export_data_desc")
                    } icon: {
                        Image("fa-file-export")
                    }
                }
                .fileExporter(isPresented: $showFileExporter, document: jsonFile, contentType: .json, defaultFilename: "flare_data_export") { result in
                    switch result {
                    case .success:
                        Drops.show(.init(stringLiteral: .init(localized: "save_completed")))
                    case .failure:
                        Drops.show(.init(stringLiteral: .init(localized: "save_error")))
                    }
                }
                
                Button {
                    guard !isClearingStorage else { return }
                    showFileImporter = true
                } label: {
                    Label {
                        Text("settings_storage_import_data")
                        Text("settings_storage_import_data_desc")
                    } icon: {
                        Image("fa-file-import")
                    }
                }
                .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.json]) { result in
                    switch result {
                    case .success(let url):
                        guard url.startAccessingSecurityScopedResource() else { return }
                        defer { url.stopAccessingSecurityScopedResource() }
                        
                        do {
                            let data = try Data(contentsOf: url)
                            if let json = String(data: data, encoding: .utf8) {
                                pendingImportJson = json
                                showImportConfirmation = true
                            }
                        } catch {
                            Drops.show(.init(stringLiteral: .init(localized: "import_error")))
                        }
                    case .failure:
                        Drops.show(.init(stringLiteral: .init(localized: "import_error")))
                    }
                }
                .alert("import_confirmation_title", isPresented: $showImportConfirmation) {
                    Button("Cancel", role: .cancel) {
                        showImportConfirmation = false
                        pendingImportJson = nil
                    }
                    Button("Ok") {
                        if let json = pendingImportJson {
                            let importPresenter = ImportDataPresenter(jsonContent: json)
                            Task {
                                do {
                                    try await importPresenter.models.value.import()
                                    Drops.show(.init(stringLiteral: .init(localized: "import_completed")))
                                } catch {
                                    Drops.show(.init(stringLiteral: .init(localized: "import_error")))
                                }
                            }
                        }
                        showImportConfirmation = false
                        pendingImportJson = nil
                    }
                } message: {
                    Text("import_confirmation_message")
                }
            }
            .disabled(isClearingStorage)
            if isClearingStorage {
                Color.black.opacity(0.2)
                    .ignoresSafeArea()
                ProgressView()
                    .controlSize(.large)
            }
        }
        .navigationTitle("storage_title")
    }
}

struct JSONFile: FileDocument {
    static let readableContentTypes = [UTType.json]
    var text = ""

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        if let data = configuration.file.regularFileContents {
            text = String(decoding: data, as: UTF8.self)
        }
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let data = Data(text.utf8)
        return FileWrapper(regularFileWithContents: data)
    }
}
