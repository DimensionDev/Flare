import SwiftUI
@preconcurrency import KotlinSharedUI
import Kingfisher
import UniformTypeIdentifiers
import Drops

struct StorageScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: StoragePresenter())
    @StateObject private var exportPresenter = KotlinPresenter(presenter: ExportDataPresenter())
    @State private var showDatabaseClearAlert = false
    @State private var showImageClearAlert = false
    @State private var showFileExporter = false
    @State private var showFileImporter = false
    @State private var jsonFile: JSONFile?
    var body: some View {
        List {
            Button(role: .destructive) {
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
                    KingfisherManager.shared.cache.clearMemoryCache()
                    KingfisherManager.shared.cache.clearDiskCache {
                    }
                }
            }
            
            
            Button(role: .destructive) {
//                presenter.state.clearCache()
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
                    presenter.state.clearCache()
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
                Task {
                    do {
                        let json = try await exportPresenter.state.export()
                        self.jsonFile = JSONFile(text: json)
                        self.showFileExporter = true
                    } catch {
                        print(error)
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
                case .success(let url):
                    print("Saved to \(url)")
                    // show success message
                    Drops.show(.init(stringLiteral: "save_completed"))
                case .failure(let error):
                    print(error.localizedDescription)
                }
            }
            
            Button {
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
                            let importPresenter = ImportDataPresenter(jsonContent: json)
                            Task {
                                do {
                                    try await importPresenter.models.value.import()
                                    Drops.show(.init(stringLiteral: "save_completed"))
                                } catch {
                                    Drops.show(.init(stringLiteral: "error"))
                                }
                            }
                        }
                    } catch {
                        print(error.localizedDescription)
                    }
                case .failure(let error):
                    print(error.localizedDescription)
                }
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
