import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

public struct DiscoverContentScreen<AskAiOverlay: View>: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<DiscoverState>
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @StateObject private var searchHistoryPresenter = KotlinPresenter(presenter: SearchHistoryPresenter())
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
        List {
            if searchPresenter.state.searching {
                searchResultContent
            } else {
                discoverContent
            }
        }
        .modifier(DiscoverListStyle())
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
            ToolbarItem(placement: .primaryAction) {
                Button {
                    refresh()
                } label: {
                    Label {
                        Text("action_refresh", bundle: FlareAppleUILocalization.bundle)
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
                searchPresenter.state.search(query: "")
            } else if !isSearchPresented && searchText.isEmpty && !committedSearchText.isEmpty {
                DispatchQueue.main.async {
                    searchText = committedSearchText
                }
            }
        }
        .onChange(of: presenter.state.selectedAccount) { _, newAccount in
            if let newAccount {
                searchPresenter.state.setAccount(profile: newAccount)
            }
        }
    }

    #if os(iOS)
    @ToolbarContentBuilder
    private var accountToolbarItem: some ToolbarContent {
        if case .success(let data) = onEnum(of: presenter.state.accounts) {
            let accounts = data.data
            if accounts.count > 1 {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        ForEach(0..<accounts.count, id: \.self) { index in
                            let account = accounts[index] as! UiProfile
                            Toggle(isOn: Binding(get: {
                                presenter.state.selectedAccount?.key == account.key
                            }, set: { value in
                                if value {
                                    presenter.state.setAccount(profile: account)
                                    searchPresenter.state.setAccount(profile: account)
                                }
                            })) {
                                Label {
                                    Text(account.handle.canonical)
                                } icon: {
                                    AvatarView(data: account.avatar?.url, customHeader: account.avatar?.customHeaders)
                                }
                            }
                        }
                    } label: {
                        if let selectedAccount = presenter.state.selectedAccount {
                            HStack(spacing: 8) {
                                AvatarView(
                                    data: selectedAccount.avatar?.url,
                                    customHeader: selectedAccount.avatar?.customHeaders
                                )
                                .frame(width: 24, height: 24)
                                Text(selectedAccount.handle.canonical)
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
                        }
                    }
                }
            }
        }
    }
    #endif

    #if os(macOS)
    @ToolbarContentBuilder
    private var macAccountToolbarItem: some ToolbarContent {
        if case .success(let data) = onEnum(of: presenter.state.accounts) {
            let rawAccounts = data.data
            let accounts: [UiProfile] = (0..<rawAccounts.count).compactMap { index in
                rawAccounts[index] as? UiProfile
            }
            if !accounts.isEmpty, let toolbarAccount = presenter.state.selectedAccount ?? accounts.first {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isMacAccountPopoverPresented.toggle()
                    } label: {
                        AvatarView(
                            data: toolbarAccount.avatar?.url,
                            customHeader: toolbarAccount.avatar?.customHeaders
                        )
                        .frame(width: 26, height: 26)
                    }
                    .buttonStyle(.plain)
                    .help(toolbarAccount.handle.canonical)
                    .popover(isPresented: $isMacAccountPopoverPresented, arrowEdge: .top) {
                        MacDiscoverAccountPopover(
                            accounts: accounts,
                            selectedAccount: presenter.state.selectedAccount,
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
                titleKey: "search_users",
                usersState: usersState,
                openURL: openURL
            )
        }

        if !searchPresenter.state.status.isEmpty && !searchPresenter.state.status.isError {
            DiscoverTimelineSection(
                titleKey: "search_status",
                data: searchPresenter.state.status
            )
        }
    }

    @ViewBuilder
    private var discoverContent: some View {
        if case .success(let usersState) = onEnum(of: presenter.state.users) {
            DiscoverUserSection(
                titleKey: "discover_users",
                usersState: usersState,
                openURL: openURL
            )
        }

        if case .success(let tagState) = onEnum(of: presenter.state.hashtags) {
            DiscoverHashtagSection(
                hashtagsState: tagState,
                onSelect: commitSearch
            )
        }

        if !presenter.state.status.isEmpty && !presenter.state.status.isError {
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
        searchPresenter.state.search(query: query)
        isSearchPresented = false
    }

    private func askAi() {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        isSearchPresented = false
        onAskAi(query.isEmpty ? nil : query)
    }

    private func selectAccount(_ account: UiProfile) {
        presenter.state.setAccount(profile: account)
        searchPresenter.state.setAccount(profile: account)
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
    let selectedAccount: UiProfile?
    let onSelect: (UiProfile) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(0..<accounts.count, id: \.self) { index in
                let account = accounts[index]
                MacDiscoverAccountRow(
                    account: account,
                    isSelected: selectedAccount?.key == account.key,
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

                AvatarView(data: account.avatar?.url, customHeader: account.avatar?.customHeaders)
                    .frame(width: 28, height: 28)

                Text(account.handle.canonical)
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
    let openURL: OpenURLAction

    var body: some View {
        Section {
            ScrollView(.horizontal) {
                LazyHStack(spacing: 8) {
                    ForEach(0..<Int(usersState.itemCount), id: \.self) { index in
                        ListCardView {
                            if let item = usersState.peek(index: Int32(index)) {
                                UserCompatView(data: item)
                                    .onAppear {
                                        _ = usersState.get(index: Int32(index))
                                    }
                                    .padding()
                                    .onTapGesture {
                                        item.onClicked(
                                            ClickContext(
                                                launcher: AppleUriLauncher(openUrl: openURL)
                                            )
                                        )
                                    }
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
    let onSelect: (String) -> Void

    private let columns = [
        GridItem(.adaptive(minimum: 96), spacing: 8, alignment: .leading)
    ]

    var body: some View {
        Section {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                ForEach(0..<Int(hashtagsState.itemCount), id: \.self) { index in
                    if let item = hashtagsState.peek(index: Int32(index)) {
                        Button {
                            onSelect(item.hashtag)
                        } label: {
                            Text(item.hashtag)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
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
                            .frame(maxWidth: .infinity, alignment: .leading)
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
        } header: {
            Text("discover_tags", bundle: FlareAppleUILocalization.bundle)
        }
        .modifier(DiscoverSectionRowStyle())
    }
}

private struct DiscoverTimelineSection: View {
    let titleKey: String
    let data: PagingState<UiTimelineV2>

    var body: some View {
        Section {
            TimelinePagingListContent(data: data, usesDefaultHorizontalPadding: true)
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
