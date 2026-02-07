import SwiftUI
import KotlinSharedUI
import UniformTypeIdentifiers

struct RssScreen: View {
    @Environment(\.tabKey) private var tabKeyEnv
    @Environment(\.isActive) private var isActive
    @StateObject private var presenter = KotlinPresenter(presenter: RssListWithTabsPresenter())
    @State private var showAddSheet = false
    @State private var selectedEditItem: UiRssSource? = nil
    @State private var importOpmlUrl: URL? = nil
    @State private var exportedOPMLContent: String? = nil

    var body: some View {
        ScrollViewReader { proxy in
            List {
                ForEach(presenter.state.sources, id: \.id) { item in
                    NavigationLink(value: Route.timeline(RssTimelineTabItem(data: item))) {
                        HStack {
                            UiRssView(data: item)
                            Spacer()
                            Button {
                                selectedEditItem = item
                            } label: {
                                Image("fa-pen")
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .id(item.id == presenter.state.sources.first?.id ? "top" : "\(item.id)")
                    .swipeActions {
                        Button(role: .destructive) {
                        } label: {
                            Label {
                                Text("delete")
                            } icon: {
                                Image("fa-trash")
                            }
                        }
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .scrollToTop)) { notification in
                let targetTab = notification.userInfo?["tab"] as? String
                if isActive && (targetTab == nil || targetTab == tabKeyEnv) {
                    withAnimation {
                        proxy.scrollTo("top", anchor: .top)
                    }
                }
            }
            .navigationTitle("rss_title")
            .toolbar {
                toolbarContent
            }
            .sheet(isPresented: $showAddSheet) {
                addSheet
            }
            .sheet(item: $selectedEditItem) { item in
                editSheet(item: item)
            }
            .sheet(item: $importOpmlUrl) { url in
                importSheet(url: url)
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem {
            if !presenter.state.sources.isEmpty {
                Button {
                    Task {
                        exportedOPMLContent = try? await ExportOPMLPresenter().export()
                    }
                } label: {
                    Image("fa-file-export")
                }
                .fileExporter(
                    isPresented: Binding(
                        get: { exportedOPMLContent != nil },
                        set: { if !$0 { exportedOPMLContent = nil } }
                    ),
                    document: OPMLFile(initialText: exportedOPMLContent ?? ""),
                    defaultFilename: "flare_export.opml"
                ) { result in
                    exportedOPMLContent = nil
                }
            }
        }
        
        ToolbarItem(placement: .primaryAction) {
            Button {
                showAddSheet = true
            } label: {
                Image("fa-plus")
            }
        }
    }

    private var addSheet: some View {
        NavigationStack {
            EditRssSheet(id: nil, onImportOPML: { url in
                showAddSheet = false
                importOpmlUrl = url
            })
        }
    }

    private func editSheet(item: UiRssSource) -> some View {
        NavigationStack {
            EditRssSheet(id: Int(item.id), initialUrl: item.url, onImportOPML: { url in
                importOpmlUrl = url
            })
        }
    }

    private func importSheet(url: URL) -> some View {
        NavigationStack {
            ImportOPMLScreen(url: url)
        }
    }
}

struct EditRssSheet: View {
    @Environment(\.dismiss) private var dismiss
    let id: Int?
    let initialUrl: String?
    let onImportOPML: (URL) -> Void
    private let publicRssHubServer = [
        "https://rsshub.rssforever.com",
        "https://hub.slarker.me",
        "https://rsshub.pseudoyu.com"
    ]
    @StateObject private var presenter: KotlinPresenter<EditRssSourcePresenterState>
    @State private var url: String = ""
    @State private var title: String = ""
    @State private var rssHubHost: String = ""
    @State private var openInApp: Bool = true
    @State private var selectedRssSources: [UiRssSource] = []
    @State private var showFileImporter = false

    init(id: Int?, initialUrl: String? = nil, onImportOPML: @escaping (URL) -> Void) {
        self.id = id
        self.initialUrl = initialUrl
        self.onImportOPML = onImportOPML
        self._presenter = .init(wrappedValue: .init(presenter: EditRssSourcePresenter(id: id == nil ? nil : KotlinInt(value: Int32(id!)))))
    }

    var body: some View {
        Form {
            Section {
                TextField("rss_url_placeholder", text: $url)
                    .textContentType(.URL)
                    .keyboardType(.URL)
                    .safeAreaInset(edge: .trailing) {
                        checkIcon
                    }
                    .onChange(of: url) { oldValue, newValue in
                        presenter.state.checkUrl(value: newValue)
                    }
            } header: {
                Text("rss_url_header")
            } footer: {
                if url.isEmpty && id == nil {
                    Button("opml_import") {
                        showFileImporter = true
                    }
                    .fileImporter(
                        isPresented: $showFileImporter,
                        allowedContentTypes: [
                            UTType(exportedAs: "opml", conformingTo: .plainText),
                            .plainText,
                            .xml,
                            .text,
                        ]
                    ) { result in
                        switch result {
                        case .success(let url):
                            onImportOPML(url)
                            dismiss()
                        case .failure(let error):
                            print(error)
                        }
                    }
                }
            }
            StateView(state: presenter.state.checkState) { state in
                checkResultView(state: state)
            }
        }
        .onChange(of: presenter.state.checkState, { oldValue, newValue in
            selectedRssSources = []
            rssHubHost = ""
            if case .success(let success) = onEnum(of: newValue), case .rssFeed(let feed) = onEnum(of: success.data) {
                if title.isEmpty {
                    title = feed.title
                }
            }
        })
        .onChange(of: presenter.state.data, { oldValue, newValue in
            if case .success(let success) = onEnum(of: newValue) {
                title = success.data.title ?? ""
            }
        })
        .navigationTitle(id == nil ? "add_rss_title" : "edit_rss_title")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(role: .cancel) {
                    dismiss()
                } label: {
                    Label { Text("Cancel") } icon: { Image("fa-xmark") }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    saveAction()
                    dismiss()
                } label: {
                    Label { Text("Done") } icon: { Image("fa-check") }
                }
            }
        }
        .onAppear {
            if let initialUrl = initialUrl, !initialUrl.isEmpty {
                self.url = initialUrl
                presenter.state.checkUrl(value: initialUrl)
            }
        }
    }

    @ViewBuilder
    private var checkIcon: some View {
        StateView(state: presenter.state.checkState) { (state: CheckRssSourcePresenterStateRssState) in
            switch onEnum(of: state) {
            case .rssFeed:
                Image("fa-circle-check").foregroundColor(.green)
            case .rssHub, .rssSources:
                Image("fa-circle-chevron-down").foregroundColor(.secondary)
            }
        } errorContent: { _ in
            Image("fa-circle-exclamation").foregroundColor(.red)
        } loadingContent: {
            ProgressView().frame(width: 20, height: 20)
        }
    }

    @ViewBuilder
    private func checkResultView(state: CheckRssSourcePresenterStateRssState) -> some View {
        Group {
            switch onEnum(of: state) {
            case .rssFeed(let rssFeed):
                Section {
                    TextField(text: $title) { Text("rss_item_title") }
                    .safeAreaInset(edge: .leading) {
                        if let favIcon = rssFeed.icon, !favIcon.isEmpty {
                            NetworkImage(data: favIcon).frame(width: 24, height: 24)
                        } else {
                            Image("fa-square-rss")
                        }
                    }
                    Text(rssFeed.url).font(.caption).foregroundStyle(.secondary)
                } header: { Text("rss_feed_header") }
            case .rssSources(let rssSources):
                Section {
                    ForEach(rssSources.sources, id: \.url) { item in
                        HStack {
                            UiRssView(data: item)
                            Spacer()
                            Image(systemName: selectedRssSources.contains(where: { $0.url == item.url }) ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(.blue)
                        }
                        .onTapGesture {
                            if let index = selectedRssSources.firstIndex(where: { $0.url == item.url }) {
                                selectedRssSources.remove(at: index)
                            } else {
                                selectedRssSources.append(item)
                            }
                        }
                    }
                } header: { Text("rss_sources_header") }
            case .rssHub:
                rssHubSection
            }
            
            Section {
                Picker("rss_open_in", selection: $openInApp) {
                    Text("rss_open_in_app").tag(true)
                    Text("rss_open_in_browser").tag(false)
                }
            }
        }
    }

    @ViewBuilder
    private var rssHubSection: some View {
        Section {
            TextField(text: $title) { Text("rss_item_title") }
            TextField(text: $rssHubHost) { Text("rss_hub_host_placeholder") }
            .textContentType(.URL).keyboardType(.URL)
            .safeAreaInset(edge: .trailing) {
                StateView(state: presenter.state.inputState) { inputState in
                    if case .rssHub(let rssHub) = onEnum(of: inputState) {
                        StateView(state: rssHub.checkState) { _ in
                            Image("fa-circle-check").foregroundColor(.green)
                        } errorContent : { _ in
                            Image("fa-circle-exclamation").foregroundColor(.red)
                        } loadingContent: {
                            ProgressView().frame(width: 20, height: 20)
                        }
                    }
                }
            }
            .onChange(of: presenter.state.inputState, { oldValue, newValue in
                if case .success(let success) = onEnum(of: newValue),
                   case .rssHub(let rssHubState) = onEnum(of: success.data),
                   case .success(let checkSuccess) = onEnum(of: rssHubState.checkState),
                   case .rssFeed(let feed) = onEnum(of: checkSuccess.data) {
                    title = feed.title
                }
            })
            .onChange(of: rssHubHost) { oldValue, newValue in
                if case .success(let inputState) = onEnum(of: presenter.state.inputState) {
                    if case .rssHub(let rssHub) = onEnum(of: inputState.data) {
                        rssHub.checkWithServer(server: rssHubHost)
                    }
                }
            }
        } header: { Text("rss_hub_header") }
        
        Section {
            ForEach(publicRssHubServer, id: \.self) { server in
                Text(server).onTapGesture { rssHubHost = server }
            }
        } header: { Text("rss_hub_server_header") }
    }

    private func saveAction() {
        if case .success(let success) = onEnum(of: presenter.state.inputState) {
            switch onEnum(of: success.data) {
            case .rssFeed(let feed):
                feed.save(title: title, openInBrowser: !openInApp)
            case .rssHub(let rssHub):
                rssHub.save(title: title, openInBrowser: !openInApp)
            case .rssSources(let rssSources):
                rssSources.save(sources: selectedRssSources, openInBrowser: !openInApp)
            }
        }
    }
}

extension UiRssSource: Identifiable {}
extension URL: Identifiable {
    public var id: String { absoluteString }
}
