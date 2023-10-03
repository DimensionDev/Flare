import SwiftUI
import shared

struct MastodonCallbackScreen: View {
    @State var viewModel: MastodonCallbackViewModel
    init(code: String, toHome: @escaping () -> Void) {
        viewModel = MastodonCallbackViewModel(code: code, toHome: toHome)
    }
    var body: some View {
        VStack {
            Spacer()
                .frame(height: 96)
            Text("Login to Mastodon")
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
            
            if case .error(let error) = onEnum(of: viewModel.model) {
                Text(error.throwable.message ?? "error")
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
class MastodonCallbackViewModel: MoleculeViewModelProto {
    var model: Model
    typealias Model = UiState<KotlinNothing>
    typealias Presenter = MastodonCallbackPresenter
    let presenter: MastodonCallbackPresenter
    
    init(code: String, toHome: @escaping () -> Void) {
        self.presenter = MastodonCallbackPresenter(code: code, toHome: toHome)
        self.model = presenter.models.value!
    }
}

#Preview {
    MastodonCallbackScreen(code: "", toHome: {})
}
