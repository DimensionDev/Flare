import SwiftUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

public struct SearchScreen: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    private let onAskAi: (String?) -> Void
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @StateObject private var searchHistoryPresenter: KotlinPresenter<SearchHistoryState>
    @State private var searchText = ""
    @State private var committedSearchText: String
    @State private var isSearchPresented = false
    @State private var didRecordInitialQuery = false
    #if os(macOS)
    @State private var isMacAccountPopoverPresented = false
    #endif

    public init(
        accountType: AccountType,
        initialQuery: String,
        onAskAi: @escaping (String?) -> Void = { _ in }
    ) {
        self.onAskAi = onAskAi
        self._searchPresenter = .init(wrappedValue: .init(presenter: SearchPresenter(accountType: accountType, initialQuery: initialQuery)))
        self._searchHistoryPresenter = .init(wrappedValue: .init(presenter: SearchHistoryPresenter()))
        self._searchText = .init(initialValue: initialQuery)
        self._committedSearchText = .init(initialValue: initialQuery)
    }

    public var body: some View {
        #if os(iOS)
        content
            .askAiSearchOverlay(
                agentEnabled: agentEnabled,
                isSearchPresented: isSearchPresented
            ) {
                askAi()
            }
        #else
        content
        #endif
    }

    private var content: some View {
        List {
            if case .success(let usersState) = onEnum(of: searchPresenter.state.users) {
                Section {
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 8) {
                            ForEach(0..<usersState.itemCount, id: \.self) { index in
                                ListCardView {
                                    if let item = usersState.peek(index: index) {
                                        UserCompatView(data: item)
                                            .onAppear {
                                                _ = usersState.get(index: index)
                                            }
                                            .padding()
                                            .onTapGesture {
                                                item.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
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
                    Text("local_history_user", bundle: FlareAppleUILocalization.bundle)
                }
                .padding(.horizontal)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
            }
            Section {
                TimelinePagingListContent(data: searchPresenter.state.status)
            } header: {
                Text("local_history_status", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .scrollContentBackground(.hidden)
        .searchListRowSpacing(2)
        .listStyle(.plain)
        .refreshable {
            try? await searchPresenter.state.refreshSuspend()
        }
        .background(Color.flareSystemGroupedBackground)
        .navigationTitle(Text("search", bundle: FlareAppleUILocalization.bundle))
        .toolbar {
            #if os(macOS)
            askAiToolbarItem
            #endif
            accountToolbarItem
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
        .onSubmit(of: .search) {
            commitSearch(searchText)
        }
        .detectScrolling()
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
        .onAppear {
            guard !didRecordInitialQuery else { return }
            didRecordInitialQuery = true
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !query.isEmpty {
                searchHistoryPresenter.state.addSearchHistory(keyword: query)
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

    #if os(iOS)
    @ToolbarContentBuilder
    private var accountToolbarItem: some ToolbarContent {
        if case .success(let data) = onEnum(of: searchPresenter.state.accounts) {
            let accounts = data.data
            if accounts.count > 1 {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        ForEach(0..<accounts.count, id: \.self) { index in
                            let account = accounts[index] as! UiProfile
                            Toggle(isOn: Binding(get: {
                                searchPresenter.state.selectedAccount?.key == account.key
                            }, set: { value in
                                if value {
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
                        if let selectedAccount = searchPresenter.state.selectedAccount {
                            HStack {
                                AvatarView(data: selectedAccount.avatar?.url, customHeader: selectedAccount.avatar?.customHeaders)
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
    private var askAiToolbarItem: some ToolbarContent {
        if agentEnabled {
            ToolbarItem(placement: .primaryAction) {
                AskAiSearchToolbarButton(action: askAi)
            }
        }
    }

    @ToolbarContentBuilder
    private var accountToolbarItem: some ToolbarContent {
        if case .success(let data) = onEnum(of: searchPresenter.state.accounts) {
            let rawAccounts = data.data
            let accounts: [UiProfile] = (0..<rawAccounts.count).compactMap { index in
                rawAccounts[index] as? UiProfile
            }
            if !accounts.isEmpty, let toolbarAccount = searchPresenter.state.selectedAccount ?? accounts.first {
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
                        MacSearchAccountPopover(
                            accounts: accounts,
                            selectedAccount: searchPresenter.state.selectedAccount,
                            onSelect: selectMacAccount
                        )
                    }
                }
            }
        }
    }

    private func selectMacAccount(_ account: UiProfile) {
        searchPresenter.state.setAccount(profile: account)
        isMacAccountPopoverPresented = false
    }
    #endif
}

private extension View {
    @ViewBuilder
    func searchListRowSpacing(_ spacing: CGFloat?) -> some View {
        #if os(iOS)
        listRowSpacing(spacing)
        #else
        self
        #endif
    }
}

#if os(macOS)
private struct MacSearchAccountPopover: View {
    let accounts: [UiProfile]
    let selectedAccount: UiProfile?
    let onSelect: (UiProfile) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(0..<accounts.count, id: \.self) { index in
                let account = accounts[index]
                MacSearchAccountRow(
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

private struct MacSearchAccountRow: View {
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

public struct AskAiSearchOverlayModifier: ViewModifier {
    private let agentEnabled: Bool
    private let isSearchPresented: Bool
    private let bottomInset: CGFloat
    private let action: () -> Void

    public init(
        agentEnabled: Bool,
        isSearchPresented: Bool,
        bottomInset: CGFloat,
        action: @escaping () -> Void
    ) {
        self.agentEnabled = agentEnabled
        self.isSearchPresented = isSearchPresented
        self.bottomInset = bottomInset
        self.action = action
    }

    private var isVisible: Bool {
        agentEnabled && isSearchPresented
    }

    public func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if isVisible {
                    AskAiSearchAccessory(action: action)
                        .padding(.bottom, bottomInset)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .zIndex(1)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: isVisible)
    }
}

public extension View {
    func askAiSearchOverlay(
        agentEnabled: Bool,
        isSearchPresented: Bool,
        bottomInset: CGFloat = 16,
        action: @escaping () -> Void
    ) -> some View {
        modifier(
            AskAiSearchOverlayModifier(
                agentEnabled: agentEnabled,
                isSearchPresented: isSearchPresented,
                bottomInset: bottomInset,
                action: action
            )
        )
    }
}

public struct AskAiSearchAccessory: View {
    private let action: () -> Void

    public init(action: @escaping () -> Void) {
        self.action = action
    }

    public var body: some View {
        HStack {
            Button(action: action) {
                Label {
                    Text("ask_ai", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(fontAwesome: .robot)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(.horizontal)
    }
}

public struct AskAiSearchToolbarButton: View {
    private let action: () -> Void

    public init(action: @escaping () -> Void) {
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Label {
                Text("ask_ai", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(fontAwesome: .robot)
            }
        }
        .buttonStyle(.borderedProminent)
        .help(String(localized: "ask_ai", bundle: FlareAppleUILocalization.bundle))
        .accessibilityLabel(Text("ask_ai", bundle: FlareAppleUILocalization.bundle))
    }
}
