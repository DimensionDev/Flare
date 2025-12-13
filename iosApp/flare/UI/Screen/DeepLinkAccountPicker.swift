import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct DeepLinkAccountPicker: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let originalUrl: String
    let data: [MicroBlogKey : Route]
    let onNavigate: (Route) -> Void
    
    var body: some View {
        List {
            ForEach(data.keys.sorted(by: { $0.id < $1.id }), id: \.self) { userKey in
                if let route = data[userKey] {
                    Button {
                        onNavigate(route)
                        dismiss()
                    } label: {
                        UserItemView(userKey: userKey)
                    }
                }
            }
            Button {
                openURL(URL(string: originalUrl)!)
                dismiss()
            } label: {
                Label {
                    Text("deep_link_account_picker_open_in_browser")
                } icon: {
                    Image(.faGlobe)
                }

            }
        }
        .navigationTitle("deep_link_account_picker_title")
        .backport
        .navigationSubtitle("deep_link_account_picker_subtitle")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
        }
    }
}

private struct UserItemView : View {
    @StateObject private var presenter: KotlinPresenter<UserState>
    
    init(userKey: MicroBlogKey) {
        self._presenter = .init(wrappedValue: .init(presenter: UserPresenter(accountType: AccountType.Specific(accountKey: userKey), userKey: nil)))
    }
    
    var body: some View {
        StateView(state: presenter.state.user) { user in
            UserCompatView(data: user)
        } errorContent: { error in
            UserErrorView(error: error)
        } loadingContent: {
            UserLoadingView()
        }
    }
}
