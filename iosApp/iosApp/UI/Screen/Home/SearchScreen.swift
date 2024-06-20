import SwiftUI
import shared

struct SearchScreen: View {
    var searchText: String = ""
    private let onUserClicked: (UiUser) -> Void
    let presenter: SearchPresenter
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    init(accountType: AccountType, initialQuery: String, onUserClicked: @escaping (UiUser) -> Void) {
        self.onUserClicked = onUserClicked
        presenter = .init(accountType: accountType, initialQuery: initialQuery)
    }
    var body: some View {
        Observing(presenter.models) { state in
            List {
                switch onEnum(of: state.users) {
                case .success(let data):
                    Section("search_users_title") {
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
                Section("search_status_title") {
                    StatusTimelineComponent(
                        data: state.status,
                        mastodonEvent: statusEvent,
                        misskeyEvent: statusEvent,
                        blueskyEvent: statusEvent,
                        xqtEvent: statusEvent
                    )
                }
            }
        }
    }
}
