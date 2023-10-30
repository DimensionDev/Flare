import SwiftUI
import shared

struct BlueskyLoginScreen: View {
    @State var viewModel: BlueskyLoginViewModel
    init(toHome: @escaping () -> Void) {
        viewModel = BlueskyLoginViewModel(toHome: toHome)
    }
    var body: some View {
        VStack {
            Spacer()
                .frame(height: 96)
            Text("Login to Bluesky")
                .font(.title)
                .fontWeight(.bold)
            
            Text("Please enter your login credentials below.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            Spacer()
                .frame(height: 16)
            
            VStack {
                TextField("Base URL, e.g. https://bsky.social", text: $viewModel.baseUrl)
                    .disableAutocorrection(true)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .disabled(viewModel.model.loading)
                
                TextField("User Name", text: $viewModel.username)
                    .disableAutocorrection(true)
                    .textInputAutocapitalization(.never)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .disabled(viewModel.model.loading)
                
                SecureField("Password", text: $viewModel.password)
                    .disableAutocorrection(true)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .disabled(viewModel.model.loading)
            }
            .padding()
            
            Button(action: {
                viewModel.model.login(baseUrl: viewModel.baseUrl, username: viewModel.username, password: viewModel.password)
            }) {
                Text("Confirm")
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(10)
            }
            .disabled(viewModel.model.loading)
            if (viewModel.model.error != nil) {
                Text(viewModel.model.error?.message ?? "error")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

@Observable
class BlueskyLoginViewModel : MoleculeViewModelProto {
    var model: Model
    typealias Model = BlueskyLoginState
    typealias Presenter = BlueskyLoginPresenter
    let presenter: BlueskyLoginPresenter
    var baseUrl: String = "https://bsky.social"
    var username: String = ""
    var password: String = ""
    
    init(toHome: @escaping () -> Void) {
        self.presenter = BlueskyLoginPresenter(toHome: toHome)
        self.model = presenter.models.value!
    }
}

#Preview {
    BlueskyLoginScreen(toHome: {})
}
