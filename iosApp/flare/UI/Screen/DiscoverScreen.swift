import SwiftUI
@preconcurrency import KotlinSharedUI
import Flow

struct DiscoverScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<DiscoverState>
    @StateObject private var searchPresenter: KotlinPresenter<SearchState>
    @State var searchText = ""
    
    init(accountType: AccountType) {
        self._presenter = .init(wrappedValue: .init(presenter: DiscoverPresenter(accountType: accountType)))
        self._searchPresenter = .init(wrappedValue: .init(presenter: SearchPresenter(accountType: accountType, initialQuery: "")))
    }
    
    var body: some View {
        List {
            if searchPresenter.state.searching {
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
                
            } else {
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
                if !presenter.state.status.isEmpty && !presenter.state.status.isEmpty {
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
    }
}
