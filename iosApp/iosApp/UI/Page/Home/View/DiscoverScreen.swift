import SwiftUI
import shared
import Combine

struct DiscoverScreen: View {
    private let onUserClicked: (UiUserV2) -> Void
    @State private var searchPresenter: SearchPresenter
    @State private var presenter: DiscoverPresenter
    @State var searchText = ""
    @Environment(\.horizontalSizeClass) var horizontalSizeClass

    init(accountType: AccountType, onUserClicked: @escaping (UiUserV2) -> Void) {
        self.onUserClicked = onUserClicked
        searchPresenter = .init(accountType: accountType, initialQuery: "")
        presenter = .init(accountType: accountType)
    }

    var body: some View {
        ObservePresenter(presenter: searchPresenter) { searchState in
            ObservePresenter(presenter: presenter) { state in

                List {
                    if searchState.searching {
                        switch onEnum(of: searchState.users) {
                        case .success(let data):
                            Section("discover_users") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack {
                                        ForEach(0..<data.itemCount, id: \.self) { index in
                                            if let item = data.peek(index: index) {
                                                UserComponent(
                                                    user: item,
                                                    topEndContent:nil,
                                                    onUserClicked: {
                                                        onUserClicked(item)
                                                    }
                                                )
                                                .frame(width: 200, alignment: .leading)
                                                .onAppear {
                                                    data.get(index: index)
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
                        Section("discover_status") {
                            StatusTimelineComponent(
                                data: searchState.status,
                                detailKey: nil
                            )
                        }
                    } else {
                        switch onEnum(of: state.users) {
                        case .success(let data):
                            Section("discover_users") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHGrid(rows: [.init(), .init()]) {
                                        ForEach(0..<data.itemCount, id: \.self) { index in
                                            if let item = data.peek(index: index) {
                                                UserComponent(
                                                    user: item,
                                                    topEndContent:nil,

                                                    onUserClicked: {
                                                        onUserClicked(item)
                                                    }
                                                )
                                                .frame(width: 200, alignment: .leading)
                                                .onAppear {
                                                    data.get(index: index)
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
                        default:
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                        switch onEnum(of: state.hashtags) {
                        case .success(let data):
                            Section("discover_hashtags") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack {
                                        ForEach(0..<data.itemCount, id: \.self) { index in
                                            if let item = data.peek(index: index) {
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
                                                .onAppear {
                                                    data.get(index: index)
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
                        default:
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                        if case .success(let data) = onEnum(of: state.status) {
                            Section("discover_status") {
                                StatusTimelineComponent(
                                    data: state.status,
                                    detailKey: nil
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
                .navigationTitle("home_tab_discover_title")
            }
        }
    }
}
