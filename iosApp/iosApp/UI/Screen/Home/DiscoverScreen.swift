import SwiftUI
import shared
import Combine

struct DiscoverScreen: View {
    @State private var searchViewModel = SearchViewModel(initialQuery: "")
    @State private var viewModel = DiscoverViewModel()
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    var body: some View {
        List {
            if searchViewModel.model.searching {
                switch onEnum(of: searchViewModel.model.users) {
                case .success(let data):
                    Section("discover_users_title") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack {
                                ForEach(0..<data.data.itemCount, id: \.self) { index in
                                    if let item = data.data.peek(index: index) {
                                        UserComponent(user: item)
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
                        data: searchViewModel.model.status,
                        mastodonEvent: statusEvent,
                        misskeyEvent: statusEvent,
                        blueskyEvent: statusEvent,
                        xqtEvent: statusEvent
                    )
                }
            } else {
                switch onEnum(of: viewModel.model.users) {
                case .success(let data):
                    if data.data.isNotEmptyOrLoading {
                        Section("discover_users_title") {
                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHGrid(rows: [.init(), .init()]) {
                                    ForEach(0..<data.data.itemCount, id: \.self) { index in
                                        if let item = data.data.peek(index: index) {
                                            UserComponent(user: item)
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
                switch onEnum(of: viewModel.model.hashtags) {
                case .success(let data):
                    if data.data.isNotEmptyOrLoading {
                        Section("discover_hashtags_title") {
                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack {
                                    ForEach(0..<data.data.itemCount, id: \.self) { index in
                                        if let item = data.data.peek(index: index) {
                                            Text(item.hashtag)
                                                .padding()
                                        #if !os(macOS)
                                                .background(Color(UIColor.secondarySystemBackground))
                                        #else
                                                .background(Color(NSColor.windowBackgroundColor))
                                        #endif
                                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                                .onTapGesture {
                                                    searchViewModel.searchText = "#" + item.hashtag
                                                    searchViewModel.model.search(new: "#" + item.hashtag)
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
                if case .success(let data) = onEnum(of: viewModel.model.status), data.data.isNotEmptyOrLoading {
                    Section("discover_status_title") {
                        StatusTimelineComponent(
                            data: viewModel.model.status,
                            mastodonEvent: statusEvent,
                            misskeyEvent: statusEvent,
                            blueskyEvent: statusEvent,
                            xqtEvent: statusEvent
                        )
                    }
                }
            }
        }
        .searchable(text: $searchViewModel.searchText)
        .onSubmit(of: .search) {
            searchViewModel.model.search(new: searchViewModel.searchText)
        }
        .onChange(of: searchViewModel.searchText) {
            if searchViewModel.searchText.isEmpty {
                searchViewModel.model.search(new: "")
            }
        }
        .listStyle(.plain)
        .navigationTitle("discover_title")
        .activateViewModel(viewModel: viewModel)
        .activateViewModel(viewModel: searchViewModel)
    }
}

@Observable
class DiscoverViewModel: MoleculeViewModelBase<DiscoverState, DiscoverPresenter> {
}

#Preview {
    NavigationStack {
        DiscoverScreen()
    }
}
