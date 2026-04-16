import SwiftUI
@preconcurrency import KotlinSharedUI
import Flow
import FlareUI

struct DiscoverScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<DiscoverState>
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @State var searchText = ""
    
    init() {
        self._presenter = .init(wrappedValue: .init(presenter: DiscoverPresenter()))
        self._searchPresenter = .init(wrappedValue: .init(presenter: SearchPresenter(accountType: AccountType.Guest.shared, initialQuery: "")))
    }
    
    var body: some View {
        List {
            if searchPresenter.state.searching {
                searchResultContent
            } else {
                discoverContent
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
        .toolbar {
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
                                        AvatarView(data: account.avatar)
                                    }
                                }
                            }
                        } label: {
                            if let selectedAccount = presenter.state.selectedAccount {
                                HStack {
                                    AvatarView(data: selectedAccount.avatar)
                                        .frame(width: 24, height: 24)
                                    Text(selectedAccount.handle.canonical)
                                    Image("fa-chevron-down")
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
    
    @ViewBuilder
    private var searchResultContent: some View {
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
