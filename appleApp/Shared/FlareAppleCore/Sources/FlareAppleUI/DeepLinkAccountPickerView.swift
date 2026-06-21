import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct DeepLinkAccountPickerView<Route>: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private let originalUrl: String
    private let data: [MicroBlogKey: Route]
    private let onNavigate: (Route) -> Void

    public init(
        originalUrl: String,
        data: [MicroBlogKey: Route],
        onNavigate: @escaping (Route) -> Void
    ) {
        self.originalUrl = originalUrl
        self.data = data
        self.onNavigate = onNavigate
    }

    public var body: some View {
        List {
            ForEach(data.keys.sorted(by: { $0.id < $1.id }), id: \.self) { userKey in
                if let route = data[userKey] {
                    Button {
                        onNavigate(route)
                        dismiss()
                    } label: {
                        DeepLinkAccountRow(userKey: userKey)
                    }
                    .buttonStyle(.plain)
                }
            }

            Button {
                let routeLink = DeeplinkRoute.OpenLinkDirectly(url: originalUrl).toUri()
                if let url = URL(string: routeLink) {
                    openURL(url)
                }
                dismiss()
            } label: {
                Label {
                    Text("deep_link_account_picker_open_in_browser", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(systemName: "globe")
                }
            }
            .buttonStyle(.plain)
        }
        .navigationTitle(Text("deep_link_account_picker_title", bundle: FlareAppleUILocalization.bundle))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(systemName: "xmark")
                    }
                }
            }
        }
    }
}

private struct DeepLinkAccountRow: View {
    @StateObject private var presenter: KotlinPresenter<UserState>

    init(userKey: MicroBlogKey) {
        _presenter = .init(
            wrappedValue: .init(
                presenter: UserPresenter(
                    accountType: AccountType.Specific(accountKey: userKey),
                    userKey: nil
                )
            )
        )
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
