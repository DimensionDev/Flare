import SwiftUI
import shared

struct MastodonOAuthScreen: View {
    @State private var viewModel = MastodonOAuthViewModel()

    var body: some View {
        VStack {
            Spacer()
                .frame(height: 96)
            Text("Login to Mastodon")
                .font(.title)
                .fontWeight(.bold)

            Text("Please enter your instance URL below.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            Spacer()
                .frame(height: 16)

            TextField("Instance URL, e.g. mastodon.social", text: $viewModel.instanceURL)
                .disableAutocorrection(true)
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .padding()
                .disabled(viewModel.loading)

            Button(action: {
                Task {
                    await viewModel.confirm()
                }
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
class MastodonOAuthViewModel {
    var instanceURL: String = ""
    var loading = false
    var error: String? = nil
    
    @MainActor
    func confirm() async {
        do {
            error = nil
            let result = try await shared.MastodonCallbackPresenterKt.mastodonLoginUseCase(domain: instanceURL, applicationRepository: KoinHelper.shared.applicationRepository) { url in
                guard let url = URL(string: url) else {
                    self.error = "url is not vaild"
                    return
                }
                UIApplication.shared.open(url)
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
}

#Preview {
    MastodonOAuthScreen()
}
