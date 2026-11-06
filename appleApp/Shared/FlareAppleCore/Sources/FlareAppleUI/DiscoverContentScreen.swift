import FlareAppleCore
import Flow
@preconcurrency import KotlinSharedUI
import SwiftUI

public struct DiscoverContentScreen<AskAiOverlay: View>: View {
    @Environment(\.timelineListRenderer) private var listRenderer
    @State private var listScope = UUID().uuidString
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    @State private var presenter: KotlinPresenter<DiscoverState>
    @State private var searchPresenter: KotlinPresenter<SearchState>
    @State private var searchHistoryPresenter = KotlinPresenter(presenter: SearchHistoryPresenter())
    @State private var searchText = ""
    @State private var committedSearchText = ""
    @State private var isSearchPresented = false
    @State private var isMacAccountPopoverPresented = false

    private let onAskAi: (String?) -> Void
    private let askAiOverlay: (Bool, @escaping () -> Void) -> AskAiOverlay

    public init(
        onAskAi: @escaping (String?) -> Void = { _ in },
        @ViewBuilder askAiOverlay: @escaping (Bool, @escaping () -> Void) -> AskAiOverlay
    ) {
        self.onAskAi = onAskAi
        self.askAiOverlay = askAiOverlay
        _presenter = .init(wrappedValue: .init(presenter: DiscoverPresenter()))
        _searchPresenter = .init(
            wrappedValue: .init(
                presenter: SearchPresenter(accountType: AccountType.Guest.shared, initialQuery: "")
            )
        )
    }

    public var body: some View {
        scrollingContent
        .modifier(DiscoverListStyle())
        .overlay {
            if searchPresenter.state.searching {
                if searchPresenter.state.isSearchUnavailable || searchPresenter.state.hasNoResults {
                    ListEmptyView(
                        searchPresenter.state.isSearchUnavailable ? "search_unavailable_title" : "search_empty_title",
                        icon: .magnifyingGlass
                    )
                    .allowsHitTesting(false)
                }
            } else if presenter.state.hasNoRecommendations {
                ListEmptyView("discover_empty_title", icon: .globe)
                    .allowsHitTesting(false)
            }
        }
        .refreshable {
            if searchPresenter.state.searching {
                try? await searchPresenter.state.refreshSuspend()
            } else {
                try? await presenter.state.refreshSuspend()
            }
        }
        .background(Color.flareSystemGroupedBackground)
        .toolbar {
            #if os(macOS)
            askAiToolbarItem

            ToolbarItem(placement: .primaryAction) {
                Button {
                    refresh()
                } label: {
                    Label {
                        Text("Refresh", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .arrowsRotate)
                    }
                }
            }
            #endif

            #if os(iOS)
            accountToolbarItem
            #elseif os(macOS)
            macAccountToolbarItem
            #endif
        }
        .searchable(text: $searchText, isPresented: $isSearchPresented)
        .searchSuggestions {
            SearchHistorySuggestions(
                state: searchHistoryPresenter.state,
                searchText: searchText,
                onSelect: commitSearch,
                onDelete: searchHistoryPresenter.state.deleteSearchHistory
            )
        }
        .overlay(alignment: .bottom) {
            askAiOverlay(isSearchPresented, askAi)
        }
        .navigationTitle(Text("discover_title", bundle: FlareAppleUILocalization.bundle))
        .detectScrolling()
        .onSubmit(of: .search) {
            commitSearch(searchText)
        }
        .onChange(of: searchText) {
            if isSearchPresented && searchText.isEmpty {
                committedSearchText = ""
                search(query: "")
            } else if !isSearchPresented && searchText.isEmpty && !committedSearchText.isEmpty {
                DispatchQueue.main.async {
                    searchText = committedSearchText
                }
            }
        }
        .onChange(of: presenter.state.selectedAccountKey, initial: true) { _, _ in
            listScope = UUID().uuidString
            search(query: committedSearchText)
        }
        .onChange(of: presenter.state.isAccountReady) { _, ready in
            if ready && presenter.state.selectedAccountKey == nil {
                search(query: committedSearchText)
            }
        }
    }

    @ViewBuilder
    private var scrollingContent: some View {
        if let listRenderer {
            listRenderer(timelineListRequest)
        } else {
            List {
                if searchPresenter.state.searching {
                    searchResultContent
                } else {
                    discoverContent
                }
            }
        }
    }

    private var timelineListRequest: TimelineListRequest {
        let searching = searchPresenter.state.searching
        let users = searching ? searchPresenter.state.users : presenter.state.users
        let posts = searching ? searchPresenter.state.status : presenter.state.status
        var headers: [TimelineListHeader] = []
        if case .success(let data) = onEnum(of: users) {
            headers.append(.title(searching ? "local_history_user" : "discover_users"))
            headers.append(TimelineListHeader(id: "users") { TimelineListUserStrip(users: data) })
        } else if case .error(let error) = onEnum(of: users) {
            headers.append(TimelineListHeader(id: "users-error") {
                ListErrorView(error: error.error) { _ = error.onRetry() }
            })
        } else if case .loading = onEnum(of: users) {
            headers.append(TimelineListHeader(id: "users-loading") { SearchSectionProgress() })
        }
        if !searching {
            switch onEnum(of: presenter.state.hashtags) {
            case .success(let tags):
                headers.append(.title("discover_tags"))
                headers.append(TimelineListHeader(id: "tags") {
                    DiscoverHashtagSection(
                        hashtagsState: tags,
                        canSearchPosts: presenter.state.canSearchPosts,
                        onSelect: commitSearch
                    ).content
                        .padding(.horizontal, 16)
                })
            case .error(let error):
                headers.append(TimelineListHeader(id: "tags-error") {
                    ListErrorView(error: error.error) { _ = error.onRetry() }
                })
            case .loading:
                headers.append(TimelineListHeader(id: "tags-loading") { SearchSectionProgress() })
            case .empty:
                break
            }
        }
        let showsPosts = !posts.isEmpty
        if showsPosts { headers.append(.title(searching ? "local_history_status" : "discover_status")) }
        return TimelineListRequest(
            key: "\(listScope):\(searching ? searchPresenter.key : "discover")",
            content: showsPosts ? .posts(posts) : .none,
            headers: headers
        )
    }

    #if os(iOS)
    @ToolbarContentBuilder
    private var accountToolbarItem: some ToolbarContent {
        if case .success(let data) = onEnum(of: presenter.state.accounts) {
            let accounts = data.data
            if presenter.state.canSwitchAccount {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        ForEach(0..<accounts.count, id: \.self) { index in
                            let account = accounts[index] as! UiProfile
                            Toggle(isOn: Binding(get: {
                                presenter.state.selectedAccountKey == account.key
                            }, set: { value in
                                if value {
                                    selectAccount(account)
                                }
                            })) {
                                Label {
                                    Text(account.handle.display)
                                } icon: {
                                    AvatarView(data: account.avatar)
                                }
                            }
                        }
                    } label: {
                        if let selectedAccount = presenter.state.selectedAccount {
                            HStack(spacing: 8) {
                                AvatarView(
                                    data: selectedAccount.avatar
                                )
                                .frame(width: 24, height: 24)
                                Text(selectedAccount.handle.display)
                                Image(fontAwesome: .chevronDown)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                    .scaledToFit()
                                    .frame(width: 8, height: 8)
                                    .padding(8)
                                    .background(
                                        Circle()
                                            .fill(Color.secondary.opacity(0.2))
                                    )
                                    .scaleEffect(0.66)
                            }
                        } else {
                            Label {
                                Text("discover_title", bundle: FlareAppleUILocalization.bundle)
                            } icon: {
                                Image(systemName: "person.crop.circle")
                            }
                        }
                    }
                }
            }
        }
    }
    #endif

    #if os(macOS)
    @ToolbarContentBuilder
    private var askAiToolbarItem: some ToolbarContent {
        if agentEnabled {
            ToolbarItem(placement: .primaryAction) {
                AskAiSearchToolbarButton(action: askAi)
            }
        }
    }

    @ToolbarContentBuilder
    private var macAccountToolbarItem: some ToolbarContent {
        if case .success(let data) = onEnum(of: presenter.state.accounts) {
            let rawAccounts = data.data
            let accounts: [UiProfile] = (0..<rawAccounts.count).compactMap { index in
                rawAccounts[index] as? UiProfile
            }
            if !accounts.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isMacAccountPopoverPresented.toggle()
                    } label: {
                        if let toolbarAccount = presenter.state.selectedAccount {
                            AvatarView(
                                data: toolbarAccount.avatar
                            )
                            .frame(width: 26, height: 26)
                        } else {
                            Label {
                                Text("discover_title", bundle: FlareAppleUILocalization.bundle)
                            } icon: {
                                Image(systemName: "person.crop.circle")
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .help(presenter.state.selectedAccount?.handle.display ?? String(localized: "discover_title", bundle: FlareAppleUILocalization.bundle))
                    .popover(isPresented: $isMacAccountPopoverPresented, arrowEdge: .top) {
                        MacDiscoverAccountPopover(
                            accounts: accounts,
                            selectedAccountKey: presenter.state.selectedAccountKey,
                            onSelect: selectAccount
                        )
                    }
                }
            }
        }
    }
    #endif

    @ViewBuilder
    private var searchResultContent: some View {
        if case .success(let usersState) = onEnum(of: searchPresenter.state.users) {
            DiscoverUserSection(
                titleKey: "local_history_user",
                usersState: usersState
            )
        } else if case .error(let error) = onEnum(of: searchPresenter.state.users) {
            ListErrorView(error: error.error) { _ = error.onRetry() }
        } else if case .loading = onEnum(of: searchPresenter.state.users) {
            SearchSectionProgress()
        }

        if !searchPresenter.state.status.isEmpty {
            DiscoverTimelineSection(
                titleKey: "local_history_status",
                data: searchPresenter.state.status
            )
        }
    }

    @ViewBuilder
    private var discoverContent: some View {
        if case .success(let usersState) = onEnum(of: presenter.state.users) {
            DiscoverUserSection(
                titleKey: "discover_users",
                usersState: usersState
            )
        } else if case .error(let error) = onEnum(of: presenter.state.users) {
            ListErrorView(error: error.error) { _ = error.onRetry() }
        } else if case .loading = onEnum(of: presenter.state.users) {
            SearchSectionProgress()
        }

        if case .success(let tagState) = onEnum(of: presenter.state.hashtags) {
            DiscoverHashtagSection(
                hashtagsState: tagState,
                canSearchPosts: presenter.state.canSearchPosts,
                onSelect: commitSearch
            )
        } else if case .error(let error) = onEnum(of: presenter.state.hashtags) {
            ListErrorView(error: error.error) { _ = error.onRetry() }
        } else if case .loading = onEnum(of: presenter.state.hashtags) {
            SearchSectionProgress()
        }

        if !presenter.state.status.isEmpty {
            DiscoverTimelineSection(
                titleKey: "discover_status",
                data: presenter.state.status
            )
        }
    }

    private func refresh() {
        if searchPresenter.state.searching {
            Task {
                try? await searchPresenter.state.refreshSuspend()
            }
        } else {
            Task {
                try? await presenter.state.refreshSuspend()
            }
        }
    }

    private func commitSearch(_ rawQuery: String) {
        let query = rawQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return }

        searchText = query
        committedSearchText = query
        searchHistoryPresenter.state.addSearchHistory(keyword: query)
        search(query: query)
        isSearchPresented = false
    }

    private func search(query: String) {
        presenter.state.setSearchActive(active: !query.isEmpty)
        guard presenter.state.isAccountReady else { return }
        searchPresenter = KotlinPresenter(
            presenter: SearchPresenter(accountType: presenter.state.selectedAccountType, initialQuery: query)
        )
    }

    private func askAi() {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        isSearchPresented = false
        onAskAi(query.isEmpty ? nil : query)
    }

    private func selectAccount(_ account: UiProfile) {
        presenter.state.setAccount(profile: account)
        #if os(macOS)
        isMacAccountPopoverPresented = false
        #endif
    }
}

public extension DiscoverContentScreen where AskAiOverlay == EmptyView {
    init() {
        self.init(
            onAskAi: { _ in },
            askAiOverlay: { _, _ in
                EmptyView()
            }
        )
    }
}

#if os(macOS)
private struct MacDiscoverAccountPopover: View {
    let accounts: [UiProfile]
    let selectedAccountKey: MicroBlogKey?
    let onSelect: (UiProfile) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(0..<accounts.count, id: \.self) { index in
                let account = accounts[index]
                MacDiscoverAccountRow(
                    account: account,
                    isSelected: selectedAccountKey == account.key,
                    action: {
                        onSelect(account)
                    }
                )
            }
        }
        .padding(8)
        .frame(width: 280)
    }
}

private struct MacDiscoverAccountRow: View {
    let account: UiProfile
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                    .frame(width: 18)

                AvatarView(data: account.avatar)
                    .frame(width: 28, height: 28)

                Text(account.handle.display)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(
            isSelected ? Color.accentColor.opacity(0.12) : Color.clear,
            in: RoundedRectangle(cornerRadius: 6, style: .continuous)
        )
    }
}
#endif

private struct DiscoverUserSection: View {
    let titleKey: String
    let usersState: PagingStateSuccess<UiProfile>

    var body: some View {
        Section {
            ScrollView(.horizontal) {
                LazyHStack(spacing: 8) {
                    ForEach(0..<Int(usersState.itemCount), id: \.self) { index in
                        ListCardView {
                            if let item = usersState.peek(index: Int32(index)) {
                                SearchResultProfileView(user: item)
                                    .onAppear {
                                        _ = usersState.get(index: Int32(index))
                                    }
                                    .padding()
                            } else {
                                UserLoadingView()
                                    .padding()
                            }
                        }
                        .frame(maxWidth: 280)
                    }
                }
            }
            .scrollIndicators(.hidden)
        } header: {
            Text(FlareAppleUILocalization.string(titleKey))
        }
        .modifier(DiscoverSectionRowStyle())
    }
}

private struct DiscoverHashtagSection: View {
    let hashtagsState: PagingStateSuccess<UiHashtag>
    let canSearchPosts: Bool
    let onSelect: (String) -> Void

    var body: some View {
        Section {
            content
        } header: {
            Text("discover_tags", bundle: FlareAppleUILocalization.bundle)
        }
        .modifier(DiscoverSectionRowStyle())
    }

    var content: some View {
        HFlow(spacing: 8) {
            ForEach(0..<Int(hashtagsState.itemCount), id: \.self) { index in
                if let item = hashtagsState.peek(index: Int32(index)) {
                    Button {
                        onSelect(item.searchContent)
                    } label: {
                        Text(item.hashtag)
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canSearchPosts)
                    .background(
                        Color.flareSecondarySystemGroupedBackground,
                        in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                    )
                    .onAppear {
                        _ = hashtagsState.get(index: Int32(index))
                    }
                } else {
                    Text("#loading", bundle: FlareAppleUILocalization.bundle)
                        .lineLimit(1)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(
                            Color.flareSecondarySystemGroupedBackground,
                            in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                        )
                        .redacted(reason: .placeholder)
                }
            }
        }
    }
}

private struct DiscoverTimelineSection: View {
    let titleKey: String
    let data: PagingState<UiTimelineV2>

    var body: some View {
        Section {
            #if os(iOS)
            TimelinePagingListContent(data: data)
            #else
            TimelinePagingListContent(data: data, usesDefaultHorizontalPadding: true)
            #endif
        } header: {
            Text(FlareAppleUILocalization.string(titleKey))
        }
    }
}

private struct DiscoverListStyle: ViewModifier {
    @ViewBuilder
    func body(content: Content) -> some View {
        #if os(iOS)
        content
            .scrollContentBackground(.hidden)
            .listRowSpacing(2)
            .listStyle(.plain)
        #else
        content
            .scrollContentBackground(.hidden)
            .listStyle(.inset)
        #endif
    }
}

private struct DiscoverSectionRowStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .listRowSeparator(.hidden)
            .padding(.horizontal)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
    }
}
