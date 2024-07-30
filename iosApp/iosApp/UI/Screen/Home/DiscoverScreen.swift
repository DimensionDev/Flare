import SwiftUI
import shared
import Combine

struct DiscoverScreen: View {
    private let onUserClicked: (UiUserV2) -> Void
    let searchPresenter: SearchPresenter
    let presenter: DiscoverPresenter
    @State var searchText = ""
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    init(accountType: AccountType, onUserClicked: @escaping (UiUserV2) -> Void) {
        self.onUserClicked = onUserClicked
        searchPresenter = .init(accountType: accountType, initialQuery: "")
        presenter = .init(accountType: accountType)
    }
    var body: some View {
        Observing(presenter.models, searchPresenter.models) { state, searchState in
            List {
                if searchState.searching {
                    switch onEnum(of: searchState.users) {
                    case .success(let data):
                        Section("discover_users_title") {
                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack {
                                    ForEach(0..<data.data.itemCount, id: \.self) { index in
                                        if let item = data.data.peek(index: index) {
                                            UserComponent(
                                                user: item,
                                                onUserClicked: {
                                                    onUserClicked(item)
                                                }
                                            )
                                                .frame(width: 200, alignment: .leading)
                                                .onAppear {
                                                    data.data.get(index: index)
                                                }
                                        }
                                    }
                                }
                                .if(horizontalSizeClass != .compact) { view in
                                    view.padding(.horizontal)
                                }
                            }
                        }
                    default:
                        EmptyView()
                            .listRowSeparator(.hidden)
                    }
                    Section("discover_status_title") {
                        StatusTimelineComponent(
                            data: searchState.status
                        )
                    }
                } else {
                    switch onEnum(of: state.users) {
                    case .success(let data):
                        if data.data.isNotEmptyOrLoading {
                            Section("discover_users_title") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHGrid(rows: [.init(), .init()]) {
                                        ForEach(0..<data.data.itemCount, id: \.self) { index in
                                            if let item = data.data.peek(index: index) {
                                                UserComponent(
                                                    user: item,
                                                    onUserClicked: {
                                                        onUserClicked(item)
                                                    }
                                                )
                                                    .frame(width: 200, alignment: .leading)
                                                    .onAppear {
                                                        data.data.get(index: index)
                                                    }
                                            }
                                        }
                                    }
                                    .if(horizontalSizeClass != .compact) { view in
                                        view.padding(.horizontal)
                                    }
                                }
                            }
                            .listRowSeparator(.hidden)
                        } else {
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                    default:
                        EmptyView()
                            .listRowSeparator(.hidden)
                    }
                    switch onEnum(of: state.hashtags) {
                    case .success(let data):
                        if data.data.isNotEmptyOrLoading {
                            Section("discover_hashtags_title") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack {
                                        ForEach(0..<data.data.itemCount, id: \.self) { index in
                                            if let item = data.data.peek(index: index) {
                                                Text(item.hashtag)
                                                    .padding()
                                            #if os(iOS)
                                                    .background(Color(UIColor.secondarySystemBackground))
                                            #else
                                                    .background(Color(NSColor.windowBackgroundColor))
                                            #endif
                                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                                    .onTapGesture {
                                                        searchText = "#" + item.hashtag
                                                        searchState.search(new: "#" + item.hashtag)
                                                    }
                                            }
                                        }
                                    }
                                    .if(horizontalSizeClass != .compact) { view in
                                        view.padding(.horizontal)
                                    }
                                }
                            }
                            .listRowSeparator(.hidden)
                        } else {
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                    default:
                        EmptyView()
                            .listRowSeparator(.hidden)
                    }
                    if case .success(let data) = onEnum(of: state.status), data.data.isNotEmptyOrLoading {
                        Section("discover_status_title") {
                            StatusTimelineComponent(
                                data: state.status
                            )
                        }
                    }
                }
            }
            .searchable(text: $searchText)
            .onSubmit(of: .search) {
                searchState.search(new: searchText)
            }
            .onChange(of: searchText) {
                if searchText.isEmpty {
                    searchState.search(new: "")
                }
            }
            .listStyle(.plain)
            .navigationTitle("discover_title")
        }
    }
}
