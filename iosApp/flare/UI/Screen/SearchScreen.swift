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
    
    @ViewBuilder
    private var accountSection: some View {
        if case .success(let accounts) = onEnum(of: searchPresenter.state.accounts) {
            if accounts.data.count > 1 {
                Section {
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 8) {
                            ForEach(0..<accounts.data.count, id: \.self) { index in
                                let account = accounts.data[index] as! UiProfile
                                Button(action: {
                                    searchPresenter.state.setAccount(profile: account)
                                }) {
                                    HStack {
                                        AvatarView(data: account.avatar)
                                            .frame(width: 18, height: 18)
                                        Text(account.handle).font(.caption)
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(searchPresenter.state.selectedAccount?.key == account.key ? Color.secondary.opacity(0.2) : Color.clear)
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
}
