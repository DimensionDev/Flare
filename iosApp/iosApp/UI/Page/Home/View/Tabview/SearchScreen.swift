import shared
import SwiftUI

struct SearchScreen: View {
    var searchText: String = ""
    private let onUserClicked: (UiUserV2) -> Void
    @State private var presenter: SearchPresenter
    @Environment(\.horizontalSizeClass) var horizontalSizeClass

    init(accountType: AccountType, initialQuery: String, onUserClicked: @escaping (UiUserV2) -> Void) {
        self.onUserClicked = onUserClicked
        presenter = .init(accountType: accountType, initialQuery: initialQuery)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.users) {
                case let .success(data):
                    Section("search_users_title") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack {
                                ForEach(0 ..< data.itemCount, id: \.self) { index in
                                    if let item = data.peek(index: index) {
                                        UserComponent(
                                            user: item,
                                            topEndContent: nil,
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
                Section("search_status_title") {
                    StatusTimelineComponent(
                        data: state.status,
                        detailKey: nil
                    )
                }
            }
        }
    }
}
