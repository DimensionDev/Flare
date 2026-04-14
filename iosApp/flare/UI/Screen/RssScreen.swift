import SwiftUI
import KotlinSharedUI
import UniformTypeIdentifiers

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
                    item.type == SubscriptionType.rss
                        ? RssTimelineTabItem(data: item) as TimelineTabItem
                        : SubscriptionTimelineTabItem(data: item) as TimelineTabItem
                )) {
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
                .swipeActions {
                    Button(role: .destructive) {
                        presenter.state.delete(id: Int32(item.id))
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
        .navigationTitle("rss_title")
        .toolbar {
            if !presenter.state.sources.isEmpty {
                ToolbarItem {
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
                            set: { newValue in
                                if !newValue {
                                    exportedOPMLContent = nil
                                }
                            }
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
                EditRssSheet(id: Int(item.id), initialUrl: item.url, onImportOPML: { url in
                    importOpmlUrl = url
                })
            }
        }
        .sheet(item: $importOpmlUrl) { url in
            NavigationStack {
                ImportOPMLScreen(url: url)
            }
        }
    }
}

struct EditRssSheet: View {
    @Environment(\.dismiss) private var dismiss
    let id: Int?
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
    @State private var displayMode: RssDisplayMode = .fullContent
    @State private var selectedRssSources: [UiRssSource] = []
    @State private var selectedMastodonTypes: [SubscriptionType] = []
    @State private var showFileImporter = false
    var body: some View {
        Form {
            Section {
                TextField("rss_url_placeholder", text: $url)
                    .textContentType(.URL)
                    .keyboardType(.URL)
                    .safeAreaInset(edge: .trailing) {
                        StateView(state: presenter.state.checkState) { state in
                            switch onEnum(of: state) {
                            case .rssFeed:
                                Image("fa-circle-check").foregroundColor(.green)
                            case .rssHub:
                                Image("fa-circle-chevron-down").foregroundColor(.secondary)
                            case .rssSources:
                                Image("fa-circle-chevron-down").foregroundColor(.secondary)
                            case .mastodonInstance:
                                Image("fa-circle-check").foregroundColor(.green)
                            }
                        } errorContent: { _ in
                            Image("fa-circle-exclamation").foregroundColor(.red)
                        } loadingContent: {
                            ProgressView().frame(width: 20, height: 20)
                        }
                    }
                    .onChange(of: url) { oldValue, newValue in
                        presenter.state.checkUrl(value: newValue)
                    }
            } header: {
                Text("rss_url_header")
            } footer: {
                Text("subscription_url_hint")
                if url.isEmpty && id == nil {
                    Button("opml_import") {
                        showFileImporter = true
                    }
                    .fileImporter(
                        isPresented: $showFileImporter,
                        allowedContentTypes: [
                            .opml,
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
                switch onEnum(of: state) {
                case .rssFeed(let rssFeed):
                    Section {
                        TextField(text: $title) {
                            Text("rss_item_title")
                        }
                        .safeAreaInset(edge: .leading) {
                            if let favIcon = rssFeed.icon, !favIcon.isEmpty {
                                NetworkImage(data: favIcon)
                                    .frame(width: 24, height: 24)
                            } else {
                                Image("fa-square-rss")
                            }
                        }
                        Text(rssFeed.url)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } header: {
                        Text("rss_feed_header")
                    }
                case .rssSources(let rssSources):
                    Section {
                        ForEach(rssSources.sources, id: \.url) { item in
                            HStack {
                                UiRssView(data: item)
                                Spacer()
                                if selectedRssSources.contains(where: { $0.url == item.url }) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.blue)
                                } else {
                                    Image(systemName: "circle")
                                        .foregroundColor(.blue)
                                }
                            }
                            .onTapGesture {
                                if let index = selectedRssSources.firstIndex(where: { $0.url == item.url }) {
                                    selectedRssSources.remove(at: index)
                                } else {
                                    selectedRssSources.append(item)
                                }
                            }
                        }
                    } header: {
                        Text("rss_sources_header")
                    }
                case .rssHub:
                    Section {
                        TextField(text: $title) {
                            Text("rss_item_title")
                        }
                        TextField(text: $rssHubHost) {
                            Text("rss_hub_host_placeholder")
                        }
                        .textContentType(.URL)
                        .keyboardType(.URL)
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
                            print("Input state changed: \(newValue)")
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
                    } header: {
                        Text("rss_hub_header")
                    }
                    
                    Section {
                        ForEach(publicRssHubServer, id: \.self) { server in
                            Text(server)
                                .onTapGesture {
                                    rssHubHost = server
                                }
                        }
                    } header: {
                        Text("rss_hub_server_header")
                    }
                case .mastodonInstance(let mastodonInstance):
                    Section {
                        ForEach(mastodonInstance.availableTimelines, id: \.self) { type in
                            HStack {
                                if let icon = mastodonInstance.icon, !icon.isEmpty {
                                    NetworkImage(data: icon)
                                        .frame(width: 24, height: 24)
                                }
                                Text(labelForSubscriptionType(type))
                                Spacer()
                                if selectedMastodonTypes.contains(type) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.blue)
                                } else {
                                    Image(systemName: "circle")
                                        .foregroundColor(.blue)
                                }
                            }
                            .onTapGesture {
                                if let index = selectedMastodonTypes.firstIndex(of: type) {
                                    selectedMastodonTypes.remove(at: index)
                                } else {
                                    selectedMastodonTypes.append(type)
                                }
                            }
                        }
                    } header: {
                        Text("mastodon_available_timelines")
                    }
                }
                
                if case .mastodonInstance = onEnum(of: state) {
                    // No open-in picker for Mastodon instance subscriptions
                } else {
                    Section {
                        Picker("rss_open_in", selection: $displayMode) {
                            Text("rss_sources_full_content").tag(RssDisplayMode.fullContent)
                            Text("rss_sources_open_in_browser").tag(RssDisplayMode.openInBrowser)
                            Text("rss_sources_description_only").tag(RssDisplayMode.descriptionOnly)
                        }
                    }
                }
            }
         }
        .onChange(of: presenter.state.checkState, { oldValue, newValue in
            selectedRssSources = []
            selectedMastodonTypes = []
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
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(
//                    role: .confirm
                ) {
                    if case .success(let success) = onEnum(of: presenter.state.inputState) {
                        switch onEnum(of: success.data) {
                        case .rssFeed(let feed):
                            feed.save(title: title, displayMode: displayMode)
                        case .rssHub(let rssHub):
                            rssHub.save(title: title, displayMode: displayMode)
                        case .rssSources(let rssSources):
                            rssSources.save(sources: selectedRssSources, displayMode: displayMode)
                        case .mastodonInstance(let mastodonInstance):
                            let typeNames: [SubscriptionType: String] = [
                                SubscriptionType.mastodonTrends: String(localized: "mastodon_trending_statuses"),
                                SubscriptionType.mastodonPublic: String(localized: "mastodon_federated_timeline"),
                                SubscriptionType.mastodonLocal: String(localized: "mastodon_local_timeline"),
                            ]
                            let _ = mastodonInstance.save(selectedTypes: selectedMastodonTypes, typeNames: typeNames)
                        }
                    }
                    dismiss()
                } label: {
                    Label {
                        Text("Done")
                    } icon: {
                        Image("fa-check")
                    }
                }
            }
        }
        .onAppear {
            if !self.url.isEmpty {
                presenter.state.checkUrl(value: self.url)
            }
        }
    }
}

extension EditRssSheet {
    init(id: Int?, initialUrl: String? = nil, onImportOPML: @escaping (URL) -> Void) {
        self.id = id
        self.onImportOPML = onImportOPML
        self.url = initialUrl ?? ""
        let kotlinId = id.map { KotlinInt(value: Int32($0)) }
        self._presenter = .init(wrappedValue: .init(presenter: EditRssSourcePresenter(id: kotlinId)))
    }
}

extension UiRssSource: Identifiable {
    
}

extension URL: Identifiable {
    public var id: String { absoluteString }
}

private func labelForSubscriptionType(_ type: SubscriptionType) -> String {
    if type == SubscriptionType.mastodonTrends {
        return String(localized: "mastodon_trending_statuses")
    } else if type == SubscriptionType.mastodonPublic {
        return String(localized: "mastodon_federated_timeline")
    } else if type == SubscriptionType.mastodonLocal {
        return String(localized: "mastodon_local_timeline")
    } else {
        return type.name
    }
}
