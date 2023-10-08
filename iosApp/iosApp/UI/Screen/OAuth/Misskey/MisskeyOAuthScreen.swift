import SwiftUI
import shared

struct MisskeyOAuthScreen: View {
    @State private var viewModel = MisskeyOAuthViewModel()
    
    var body: some View {
        VStack {
            Spacer()
                .frame(height: 96)
            Text("Login to Misskey")
                .font(.title)
                .fontWeight(.bold)
            
            Text("Please enter your instance URL below.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            Spacer()
                .frame(height: 16)
            
            TextField("Instance URL, e.g. misskey.io", text: $viewModel.instanceURL)
                .disableAutocorrection(true)
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .padding()
                .disabled(viewModel.loading)
            
            Button(action: {
                viewModel.confirm()
            }) {
                Text("Confirm")
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(10)
            }
            .disabled(viewModel.loading)
            if (viewModel.error != nil) {
                Text(viewModel.error ?? "error")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }
}


@Observable
class MisskeyOAuthViewModel {
    var instanceURL: String = ""
    var loading = false
    var error: String? = nil
    
    func confirm() {
        shared.MisskeyCallbackPresenterKt.misskeyLoginUseCase(host: instanceURL) { url in
            guard let url = URL(string: url) else {
                self.error = "url is not vaild"
                return
            }
            UIApplication.shared.open(url)
        }
    }
}

#Preview {
    MisskeyOAuthScreen()
}
