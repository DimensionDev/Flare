import SwiftUI
@preconcurrency import KotlinSharedUI
import Flow

struct DiscoverScreen: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.tabKey) private var tabKeyEnv
    @Environment(\.isActive) private var isActive
    @StateObject private var presenter: KotlinPresenter<DiscoverState>
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @State var searchText = ""
    
    init() {
        self._presenter = .init(wrappedValue: .init(presenter: DiscoverPresenter()))
        self._searchPresenter = .init(wrappedValue: .init(presenter: SearchPresenter(accountType: AccountType.Guest.shared, initialQuery: "")))
    }
    
    var body: some View {
        ScrollViewReader { proxy in
            List {
                if searchPresenter.state.searching {
                    searchResultContent
                } else {
                    discoverContent
                }
            }
            .id("top")
            .onReceive(NotificationCenter.default.publisher(for: .scrollToTop)) { notification in
                let targetTab = notification.userInfo?["tab"] as? String
                if isActive && (targetTab == nil || targetTab == tabKeyEnv) {
                    withAnimation {
                        proxy.scrollTo("top", anchor: .top)
                    }
                }
            }
            .detectScrolling()
            .scrollContentBackground(.hidden)
            .listRowSpacing(2)
            .listStyle(.plain)
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("discover_title")
            .searchable(text: $searchText)
            .onSubmit(of: .search) {
                searchPresenter.state.search(new: searchText)
            }
            .onChange(of: searchText) {
                if searchText.isEmpty {
                    searchPresenter.state.search(new: "")
                }
            }
            .onChange(of: presenter.state.selectedAccount) { newAccount in
                if let newAccount = newAccount {
                    searchPresenter.state.setAccount(profile: newAccount)
                }
            }
        }
    }
    
    @ViewBuilder
    private var accountSection: some View {
        if case .success(let accounts) = onEnum(of: presenter.state.accounts) {
            if accounts.data.count > 1 {
                Section {
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 8) {
                            ForEach(0..<accounts.data.count, id: \.self) { index in
                                let account = accounts.data[index] as! UiProfile
                                Button(action: {
                                    presenter.state.setAccount(profile: account)
                                    searchPresenter.state.setAccount(profile: account)
                                }) {
                                    HStack {
                                        AvatarView(data: account.avatar)
                                            .frame(width: 18, height: 18)
                                        Text(account.handle).font(.caption)
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(presenter.state.selectedAccount?.key == account.key ? Color.secondary.opacity(0.2) : Color.clear)
                                    .cornerRadius(16)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .stroke(Color.secondary, lineWidth: 1)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .scrollIndicators(.hidden)
                }
                .listRowSeparator(.hidden)
                .padding(.horizontal)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
            }
        }
    }
    
    @ViewBuilder
    private var searchResultContent: some View {
        accountSection
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
        if !searchPresenter.state.status.isEmpty && !searchPresenter.state.status.isError {
            Section {
                TimelinePagingView(data: searchPresenter.state.status)
            } header: {
                Text("search_status")
            }
            .listRowSeparator(.hidden)
            .padding(.horizontal)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
        }
    }
    
    @ViewBuilder
    private var discoverContent: some View {
        accountSection
        
        if case .success(let usersState) = onEnum(of: presenter.state.users) {
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
                Text("discover_users")
            }
            .padding(.horizontal)
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
        }

        if case .success(let tagState) = onEnum(of: presenter.state.hashtags) {
            Section {
                HFlow {
                    ForEach(0..<tagState.itemCount, id: \.self) { index in
                        if let item = tagState.peek(index: index) {
                            ListCardView {
                                Text(item.hashtag)
                                    .padding(.horizontal)
                                    .padding(.vertical, 8)
                                    .onAppear {
                                        _ = tagState.get(index: index)
                                    }
                                    .onTapGesture {
                                        searchText = item.hashtag
                                        searchPresenter.state.search(new: item.hashtag)
                                    }
                            }
                        } else {
                            ListCardView {
                                Text("#loading")
                                    .padding(.horizontal)
                                    .padding(.vertical, 8)
                                    .redacted(reason: .placeholder)
                            }
                        }
                    }
                }
            } header: {
                Text("discover_tags")
            }
            .listRowSeparator(.hidden)
            .padding(.horizontal)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
        }
        if !presenter.state.status.isEmpty && !presenter.state.status.isError {
            Section {
                TimelinePagingView(data: presenter.state.status)
            } header: {
                Text("discover_status")
            }
            .listRowSeparator(.hidden)
            .padding(.horizontal)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
        }
    }
}
