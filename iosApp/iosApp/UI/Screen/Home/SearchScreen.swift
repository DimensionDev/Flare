import SwiftUI
import shared

struct SearchScreen: View {
    @State private var viewModel: SearchViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    init(initialQuery: String) {
        _viewModel = State(initialValue: .init(initialQuery: initialQuery))
    }
    var body: some View {
        List {
            switch onEnum(of: viewModel.model.users) {
            case .success(let data):
                Section("Users") {
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
            Section("Status") {
                StatusTimelineComponent(
                    data: viewModel.model.status,
                    mastodonEvent: statusEvent,
                    misskeyEvent: statusEvent,
                    blueskyEvent: statusEvent
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
    init(initialQuery: String) {
        presenter = SearchPresenter(initialQuery: initialQuery)
        model = presenter.models.value
    }
}
