import Combine
import shared
import SwiftUI

struct DiscoverTabScreen: View {
    @State private var searchPresenter: SearchPresenter
    @State private var presenter: DiscoverPresenter
    @State var searchText = ""
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType) {
        searchPresenter = .init(accountType: accountType, initialQuery: "")
        presenter = .init(accountType: accountType)
    }

    var body: some View {
        ObservePresenter(presenter: searchPresenter) { searchState in
            ObservePresenter(presenter: presenter) { state in
                List {
                    if searchState.searching {
                        switch onEnum(of: searchState.users) {
                        case let .success(data):
                            Section("discover_users") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack {
                                        ForEach(0 ..< data.itemCount, id: \.self) { index in
                                            if let item = data.peek(index: index) {
                                                UserComponent(
                                                    user: item,
                                                    topEndContent: nil
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
                            }.listRowBackground(theme.primaryBackgroundColor)
                        default:
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                        Section("discover_status") {
                            StatusTimelineComponent(
                                data: searchState.status,
                                detailKey: nil
                            ).listRowBackground(theme.primaryBackgroundColor)
                        }
                    } else {
                        switch onEnum(of: state.users) {
                        case let .success(data):
                            Section("discover_users") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHGrid(rows: [.init(), .init()]) {
                                        ForEach(0 ..< data.itemCount, id: \.self) { index in
                                            if let item = data.peek(index: index) {
                                                UserComponent(
                                                    user: item,
                                                    topEndContent: nil

//                                                    onUserClicked: {
//                                                        onUserClicked(item)
//                                                    }
                                                )
                                                .background(theme.secondaryBackgroundColor)
                                                .frame(width: 200, alignment: .leading)
                                                .onAppear {
                                                    data.get(index: index)
                                                }
                                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                            }
                                        }
                                    }
                                    .if(horizontalSizeClass != .compact) { view in
                                        view.padding(.horizontal)
                                    }
                                }
                            }.listRowBackground(theme.primaryBackgroundColor)
                                .listRowSeparator(.hidden)
                        default:
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                        switch onEnum(of: state.hashtags) {
                        case let .success(data):
                            Section("discover_hashtags") {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack {
                                        ForEach(0 ..< data.itemCount, id: \.self) { index in
                                            if let item = data.peek(index: index) {
                                                Text(item.hashtag)
                                                    .padding()
                                                    .clipShape(RoundedRectangle(cornerRadius: 13))
                                                    .onTapGesture {
                                                        searchText = "#" + item.hashtag
                                                        searchState.search(new: "#" + item.hashtag)
                                                    }
                                                    .background(theme.secondaryBackgroundColor)
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
                            }.listRowBackground(theme.primaryBackgroundColor)

                        default:
                            EmptyView()
                                .listRowSeparator(.hidden)
                        }
                        if case let .success(data) = onEnum(of: state.status) {
                            Section("discover_status") {
                                StatusTimelineComponent(
                                    data: state.status,
                                    detailKey: nil
                                )
                                .listRowBackground(theme.primaryBackgroundColor)
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
