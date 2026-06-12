import SwiftUI
import FlareAppleUI
import AppleFontAwesome
@preconcurrency import KotlinSharedUI
import FlareAppleCore

struct SearchScreen: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    let onAskAi: (String?) -> Void
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @StateObject private var searchHistoryPresenter: KotlinPresenter<SearchHistoryState>
    @State var searchText = ""
    @State private var committedSearchText: String
    @State private var isSearchPresented = false
    @State private var didRecordInitialQuery = false
    
    init(
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
    
    var body: some View {
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
                    Text("search_users")
                }
                .padding(.horizontal)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
            }
            Section {
                TimelinePagingView(data: searchPresenter.state.status)
            } header: {
                Text("search_status")
            }
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
        .refreshable {
            try? await searchPresenter.state.refreshSuspend()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("search_title")
        .toolbar {
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
        .searchable(text: $searchText, isPresented: $isSearchPresented)
        .askAiSearchOverlay(
            agentEnabled: agentEnabled,
            isSearchPresented: isSearchPresented
        ) {
            askAi()
        }
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
}

struct AskAiSearchOverlayModifier: ViewModifier {
    let agentEnabled: Bool
    let isSearchPresented: Bool
    let bottomInset: CGFloat
    let action: () -> Void

    private var isVisible: Bool {
        agentEnabled && isSearchPresented
    }

    func body(content: Content) -> some View {
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

extension View {
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

struct AskAiSearchAccessory: View {
    let action: () -> Void

    var body: some View {
        HStack {
            Button(action: action) {
                Label {
                    Text("ask_ai")
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
