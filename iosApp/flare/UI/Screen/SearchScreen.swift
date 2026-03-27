import SwiftUI
@preconcurrency import KotlinSharedUI

struct SearchScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @State var searchText = ""
    
    init(accountType: AccountType, initialQuery: String) {
        self._searchPresenter = .init(wrappedValue: .init(presenter: SearchPresenter(accountType: accountType, initialQuery: initialQuery)))
        self._searchText = .init(initialValue: initialQuery)
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
            .padding(.horizontal)
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
                                        AvatarView(data: account.avatar)
                                    }
                                }
                            }
                        } label: {
                            if let selectedAccount = searchPresenter.state.selectedAccount {
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
        .detectScrolling()
        .onChange(of: searchText) {
            if searchText.isEmpty {
                searchPresenter.state.search(new: "")
            }
        }
    }
}
