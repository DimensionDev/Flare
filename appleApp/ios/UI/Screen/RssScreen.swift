import AppleFontAwesome
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct RssScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: RssListWithTabsPresenter())
    @State private var showAddSheet = false
    @State private var selectedEditItem: UiRssSource? = nil
    @State private var importOpmlUrl: URL? = nil
    @State private var exportedOPMLContent: String? = nil

    var body: some View {
        List {
            ForEach(presenter.state.sources, id: \.id) { item in
                NavigationLink(value: Route.timeline(
                    presenter.state.timelineTabItem(item: item)
                )) {
                    HStack {
                        UiRssView(data: item)
                        Spacer()
                        Button {
                            selectedEditItem = item
                        } label: {
                            Image(fontAwesome: .pen)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .swipeActions {
                    Button(role: .destructive) {
                        presenter.state.delete(id: Int32(item.id))
                    } label: {
                        Label {
                            Text("delete")
                        } icon: {
                            Image(fontAwesome: .trash)
                        }
                    }
                }
            }
        }
        .navigationTitle("rss_title")
        .toolbar {
            if !presenter.state.sources.isEmpty {
                ToolbarItem {
                    Button {
                        Task {
                            exportedOPMLContent = try? await ExportOPMLPresenter().export()
                        }
                    } label: {
                        Image(fontAwesome: .fileExport)
                    }
                    .fileExporter(
                        isPresented: Binding(
                            get: { exportedOPMLContent != nil },
                            set: { newValue in
                                if !newValue {
                                    exportedOPMLContent = nil
                                }
                            }
                        ),
                        document: OPMLFile(initialText: exportedOPMLContent ?? ""),
                        defaultFilename: "flare_export.opml"
                    ) { _ in
                        exportedOPMLContent = nil
                    }
                }
            }

            ToolbarItem(placement: .primaryAction) {
                Button {
                    showAddSheet = true
                } label: {
                    Image(fontAwesome: .plus)
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack {
                EditRssSheet(id: nil, onImportOPML: { url in
                    showAddSheet = false
                    importOpmlUrl = url
                })
            }
        }
        .sheet(item: $selectedEditItem) { item in
            NavigationStack {
                EditRssSheet(
                    id: Int(item.id),
                    initialUrl: item.url,
                    initialDisplayMode: item.displayMode,
                    onImportOPML: { url in
                        importOpmlUrl = url
                    }
                )
            }
        }
        .sheet(item: $importOpmlUrl) { url in
            NavigationStack {
                ImportOPMLScreen(url: url)
            }
        }
    }
}

extension URL: Identifiable {
    public var id: String { absoluteString }
}
