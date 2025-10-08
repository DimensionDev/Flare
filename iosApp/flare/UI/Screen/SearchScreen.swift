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
