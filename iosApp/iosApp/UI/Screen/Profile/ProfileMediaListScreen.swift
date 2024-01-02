import SwiftUI
import shared

struct ProfileMediaListScreen: View {
    @State var viewModel: ProfileMediaViewModel
    init(userKey: MicroBlogKey) {
        _viewModel = State(initialValue: ProfileMediaViewModel(userKey: userKey))
    }

    var body: some View {
        ScrollView {

        }
        .activateViewModel(viewModel: viewModel)
    }
}

class ProfileMediaViewModel: MoleculeViewModelProto {
    typealias Model = ProfileMediaState
    typealias Presenter = ProfileMediaPresenter
    var model: Model
    let presenter: ProfileMediaPresenter
    init(userKey: MicroBlogKey) {
        presenter = ProfileMediaPresenter(userKey: userKey)
        model = presenter.models.value
    }

}
