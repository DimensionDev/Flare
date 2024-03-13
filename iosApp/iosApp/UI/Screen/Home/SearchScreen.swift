import SwiftUI
import shared

struct SearchScreen: View {
    private let onUserClicked: (UiUser) -> Void
    @State private var viewModel: SearchViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    init(accountType: AccountType, initialQuery: String, onUserClicked: @escaping (UiUser) -> Void) {
        self.onUserClicked = onUserClicked
        _viewModel = .init(initialValue: .init(accountType: accountType, initialQuery: initialQuery))
    }
    var body: some View {
        List {
            switch onEnum(of: viewModel.model.users) {
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
                    data: viewModel.model.status,
                    mastodonEvent: statusEvent,
                    misskeyEvent: statusEvent,
                    blueskyEvent: statusEvent,
                    xqtEvent: statusEvent
                )
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class SearchViewModel: MoleculeViewModelProto {
    let presenter: SearchPresenter
    var model: Model
    typealias Model = SearchState
    typealias Presenter = SearchPresenter
    var searchText: String = ""
    init(accountType: AccountType, initialQuery: String) {
        presenter = .init(accountType: accountType, initialQuery: initialQuery)
        model = presenter.models.value
    }
}
