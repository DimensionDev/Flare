import SwiftUI
import KotlinSharedUI

struct RssScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: RssListWithTabsPresenter())
    @State private var showAddSheet = false
    @State private var selectedEditItem: UiRssSource? = nil
    var body: some View {
        List {
            PagingView(data: presenter.state.sources) { item in
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
            } loadingContent: {
                UiListPlaceholder()
            }
        }
        .navigationTitle("rss_title")
        .toolbar {
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
                EditRssSheet(id: nil)
            }
        }
        .sheet(item: $selectedEditItem) { item in
            NavigationStack {
                EditRssSheet(id: Int(item.id), initialUrl: item.url)
            }
        }
    }
}

struct EditRssSheet: View {
    @Environment(\.dismiss) private var dismiss
    let id: Int?
    private let publicRssHubServer = [
        "https://rsshub.rssforever.com",
        "https://hub.slarker.me",
        "https://rsshub.pseudoyu.com"
    ]
    @StateObject private var presenter: KotlinPresenter<EditRssSourcePresenterState>
    @State private var url: String = ""
    @State private var title: String = ""
    @State private var rssHubHost: String = ""
    @State private var selectedRssSources: [UiRssSource] = []
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
            }
            StateView(state: presenter.state.checkState) { state in
                switch onEnum(of: state) {
                case .rssFeed(let rssFeed):
                    Section {
                        TextField(text: $title) {
                            Text("rss_item_title")
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
                }
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
                            feed.save(title: title, openInBrowser: false)
                        case .rssHub(let rssHub):
                            rssHub.save(title: title, openInBrowser: false)
                        case .rssSources(let rssSources):
                            rssSources.save(sources: selectedRssSources, openInBrowser: false)
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
    init(id: Int?, initialUrl: String? = nil) {
        self.id = id
        self.url = initialUrl ?? ""
        self._presenter = .init(wrappedValue: .init(presenter: EditRssSourcePresenter(id: id == nil ? nil : KotlinInt(value: Int32(id!)))))
    }
}

extension UiRssSource: Identifiable {
    
}
