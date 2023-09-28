import SwiftUI
import shared

struct MisskeyCallbackScreen: View {
    @State var viewModel: MisskeyCallbackViewModel
    init(session: String?, toHome: @escaping () -> Void) {
        viewModel = MisskeyCallbackViewModel(session: session, toHome: toHome)
    }
    var body: some View {
        VStack {
            Spacer()
                .frame(height: 96)
            Text("Login to Misskey")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.black)

            Text("Please wait while we verify your credentials.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            Spacer()
                .frame(height: 16)
            ProgressView()
            
            if viewModel.model is UiStateError<Unit> {
                let errorModel = viewModel.model as! UiStateError<Unit>
                Text(errorModel.throwable.message ?? "error")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
            }
        }
        .activateViewModel(viewModel: viewModel)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

@Observable
class MisskeyCallbackViewModel : MoleculeViewModelProto {
    var model: Model
    typealias Model = UiState
    typealias Presenter = MisskeyCallbackPresenter
    let presenter: MisskeyCallbackPresenter
    
    init(session: String?, toHome: @escaping () -> Void) {
        self.presenter = MisskeyCallbackPresenter(session: session, toHome: toHome)
        self.model = presenter.models.value!
    }
}


#Preview {
    MisskeyCallbackScreen(session: "", toHome: {})
}
