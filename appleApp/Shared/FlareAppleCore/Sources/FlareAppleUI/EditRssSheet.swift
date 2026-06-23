import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import UniformTypeIdentifiers

public struct EditRssSheet: View {
    @Environment(\.dismiss) private var dismiss

    private let id: Int?
    private let onImportOPML: (URL) -> Void

    private let publicRssHubServer = [
        "https://rsshub.rssforever.com",
        "https://hub.slarker.me",
        "https://rsshub.pseudoyu.com"
    ]

    @StateObject private var presenter: KotlinPresenter<EditRssSourcePresenterState>
    @State private var url: String
    @State private var title: String = ""
    @State private var rssHubHost: String = ""
    @State private var displayMode: RssDisplayMode = .fullContent
    @State private var selectedRssSources: [UiRssSource] = []
    @State private var selectedMastodonTypes: [SubscriptionType] = []
    @State private var showFileImporter = false

    public init(
        id: Int?,
        initialUrl: String? = nil,
        initialDisplayMode: RssDisplayMode? = nil,
        onImportOPML: @escaping (URL) -> Void
    ) {
        self.id = id
        self.onImportOPML = onImportOPML
        _url = State(initialValue: initialUrl ?? "")
        _displayMode = State(initialValue: initialDisplayMode ?? .fullContent)
        let kotlinId = id.map { KotlinInt(value: Int32($0)) }
        _presenter = StateObject(wrappedValue: KotlinPresenter(presenter: EditRssSourcePresenter(id: kotlinId)))
    }

    public var body: some View {
        Form {
            Section {
                HStack(spacing: 10) {
                    TextField("rss_url_placeholder", text: $url)
                        .rssURLTextFieldStyle()
                        .onSubmit {
                            presenter.state.checkUrl(value: url)
                        }
                        .onChange(of: url) { _, newValue in
                            presenter.state.checkUrl(value: newValue)
                        }

                    RssCheckStateIndicator(state: presenter.state.checkState)
                }
            } header: {
                Text("rss_url_header")
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text("subscription_url_hint")
                    if url.isEmpty && id == nil {
                        Button("opml_import") {
                            showFileImporter = true
                        }
                        .fileImporter(
                            isPresented: $showFileImporter,
                            allowedContentTypes: [.opml, .plainText, .xml, .text]
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
            }

            StateView(state: presenter.state.checkState) { state in
                switch onEnum(of: state) {
                case .rssFeed(let rssFeed):
                    Section {
                        HStack(spacing: 10) {
                            if let favIcon = rssFeed.icon, !favIcon.isEmpty {
                                NetworkImage(data: favIcon)
                                    .frame(width: 24, height: 24)
                            } else {
                                Image(fontAwesome: .squareRss)
                            }
                            TextField("rss_item_title", text: $title)
                                .rssPlainTextFieldStyle()
                        }

                        Text(rssFeed.url)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .textSelection(.enabled)
                    } header: {
                        Text("rss_feed_header")
                    }

                case .rssSources(let rssSources):
                    Section {
                        ForEach(rssSources.sources, id: \.url) { item in
                            SelectableRssSourceRow(
                                item: item,
                                isSelected: selectedRssSources.contains(where: { $0.url == item.url })
                            )
                            .onTapGesture {
                                toggleRssSource(item)
                            }
                        }
                    } header: {
                        Text("rss_sources_header")
                    }

                case .rssHub:
                    Section {
                        TextField("rss_item_title", text: $title)
                            .rssPlainTextFieldStyle()

                        HStack(spacing: 10) {
                            TextField("rss_hub_host_placeholder", text: $rssHubHost)
                                .rssURLTextFieldStyle()
                                .onSubmit {
                                    presenter.state.checkRssHubServer(value: rssHubHost)
                                }
                                .onChange(of: rssHubHost) { _, newValue in
                                    presenter.state.checkRssHubServer(value: newValue)
                                }

                            if let rssHubCheckState = presenter.state.rssHubCheckState {
                                RssCheckStateIndicator(state: rssHubCheckState)
                            }
                        }

                        if !presenter.state.actualRssHubUrl.isEmpty {
                            Text(presenter.state.actualRssHubUrl)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .textSelection(.enabled)
                        }
                    } header: {
                        Text("rss_hub_header")
                    }

                    Section {
                        ForEach(publicRssHubServer, id: \.self) { server in
                            Button(server) {
                                rssHubHost = server
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        Text("rss_hub_server_header")
                    }

                case .subscriptionInstance(let subscriptionInstance):
                    Section {
                        ForEach(subscriptionInstance.availableTimelines, id: \.self) { type in
                            SelectableSubscriptionTypeRow(
                                icon: subscriptionInstance.icon,
                                title: labelForSubscriptionType(type),
                                isSelected: selectedMastodonTypes.contains(type)
                            )
                            .onTapGesture {
                                toggleSubscriptionType(type)
                            }
                        }
                    } header: {
                        Text("mastodon_available_timelines")
                    }
                }

                if case .subscriptionInstance = onEnum(of: state) {
                    EmptyView()
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
        .rssFormStyle()
        .onChange(of: presenter.state.checkState) { _, newValue in
            selectedRssSources = []
            selectedMastodonTypes = []
            rssHubHost = ""
            if case .success(let success) = onEnum(of: newValue),
               case .rssFeed(let feed) = onEnum(of: success.data),
               title.isEmpty {
                title = feed.title
            }
        }
        .onChange(of: presenter.state.inputState) { _, newValue in
            if case .success(let success) = onEnum(of: newValue),
               case .rssHub(let rssHubState) = onEnum(of: success.data),
               case .success(let checkSuccess) = onEnum(of: rssHubState.checkState),
               case .rssFeed(let feed) = onEnum(of: checkSuccess.data),
               title.isEmpty {
                title = feed.title
            }
        }
        .onChange(of: presenter.state.data) { _, newValue in
            if case .success(let success) = onEnum(of: newValue) {
                title = success.data.title ?? ""
                displayMode = success.data.displayMode
            }
        }
        .navigationTitle(id == nil ? "add_rss_title" : "edit_rss_title")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(role: .cancel) {
                    dismiss()
                } label: {
                    Label {
                        Text("cancel_button")
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }

            ToolbarItem(placement: .confirmationAction) {
                Button {
                    save()
                    dismiss()
                } label: {
                    Label {
                        Text("ok_button")
                    } icon: {
                        Image(fontAwesome: .check)
                    }
                }
                .disabled(saveDisabled)
            }
        }
        .onAppear {
            if !url.isEmpty {
                presenter.state.checkUrl(value: url)
            }
        }
    }

    private var saveDisabled: Bool {
        guard presenter.state.canSave else {
            return true
        }

        if case .success(let success) = onEnum(of: presenter.state.checkState) {
            switch onEnum(of: success.data) {
            case .rssSources:
                return selectedRssSources.isEmpty
            case .subscriptionInstance:
                return selectedMastodonTypes.isEmpty
            case .rssFeed, .rssHub:
                return false
            }
        }

        return true
    }

    private func toggleRssSource(_ item: UiRssSource) {
        if let index = selectedRssSources.firstIndex(where: { $0.url == item.url }) {
            selectedRssSources.remove(at: index)
        } else {
            selectedRssSources.append(item)
        }
    }

    private func toggleSubscriptionType(_ type: SubscriptionType) {
        if let index = selectedMastodonTypes.firstIndex(of: type) {
            selectedMastodonTypes.remove(at: index)
        } else {
            selectedMastodonTypes.append(type)
        }
    }

    private func save() {
        guard case .success(let success) = onEnum(of: presenter.state.inputState) else {
            return
        }

        switch onEnum(of: success.data) {
        case .rssFeed(let feed):
            feed.save(title: title, displayMode: displayMode)
        case .rssHub(let rssHub):
            rssHub.save(title: title, displayMode: displayMode)
        case .rssSources(let rssSources):
            rssSources.save(sources: selectedRssSources, displayMode: displayMode)
        case .subscriptionInstance(let subscriptionInstance):
            let typeNames: [SubscriptionType: String] = [
                SubscriptionType.mastodonTrends: String(localized: "mastodon_trending_statuses", bundle: .main),
                SubscriptionType.mastodonPublic: String(localized: "mastodon_federated_timeline", bundle: .main),
                SubscriptionType.mastodonLocal: String(localized: "mastodon_local_timeline", bundle: .main)
            ]
            _ = subscriptionInstance.save(selectedTypes: selectedMastodonTypes, typeNames: typeNames)
        }
    }
}

private struct RssCheckStateIndicator: View {
    let state: UiState<CheckRssSourcePresenterStateRssState>

    var body: some View {
        StateView(state: state) { state in
            switch onEnum(of: state) {
            case .rssFeed, .subscriptionInstance:
                Image(fontAwesome: .circleCheck)
                    .foregroundStyle(.green)
            case .rssHub, .rssSources:
                Image(fontAwesome: .circleChevronDown)
                    .foregroundStyle(.secondary)
            }
        } errorContent: { _ in
            Image(fontAwesome: .circleExclamation)
                .foregroundStyle(.red)
        } loadingContent: {
            ProgressView()
                .controlSize(.small)
        }
        .frame(width: 24)
    }
}

private struct SelectableRssSourceRow: View {
    let item: UiRssSource
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 12) {
            UiRssView(data: item)
                .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(.blue)
                .imageScale(.large)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }
}

private struct SelectableSubscriptionTypeRow: View {
    let icon: String?
    let title: String
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 12) {
            if let icon, !icon.isEmpty {
                NetworkImage(data: icon)
                    .frame(width: 24, height: 24)
            } else {
                Image(fontAwesome: .squareRss)
            }

            Text(title)
                .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(.blue)
                .imageScale(.large)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }
}

private func labelForSubscriptionType(_ type: SubscriptionType) -> String {
    if type == SubscriptionType.mastodonTrends {
        return String(localized: "mastodon_trending_statuses", bundle: .main)
    } else if type == SubscriptionType.mastodonPublic {
        return String(localized: "mastodon_federated_timeline", bundle: .main)
    } else if type == SubscriptionType.mastodonLocal {
        return String(localized: "mastodon_local_timeline", bundle: .main)
    } else {
        return type.name
    }
}

private extension View {
    @ViewBuilder
    func rssURLTextFieldStyle() -> some View {
        #if os(iOS)
            self
                .textContentType(.URL)
                .keyboardType(.URL)
        #else
            self.textFieldStyle(.roundedBorder)
        #endif
    }

    @ViewBuilder
    func rssPlainTextFieldStyle() -> some View {
        #if os(macOS)
            self.textFieldStyle(.roundedBorder)
        #else
            self
        #endif
    }

    @ViewBuilder
    func rssFormStyle() -> some View {
        #if os(macOS)
            self.formStyle(.grouped)
        #else
            self
        #endif
    }
}
