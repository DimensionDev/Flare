import SwiftUI
import shared
import Awesome

struct HomeScreen: View {
    @State private var presenter = ActiveAccountPresenter()

    var body: some View {
        ObservePresenter(presenter: presenter) { userState in
            let accountType: AccountType? = switch onEnum(of: userState.user) {
            case .success(let data): AccountTypeSpecific(accountKey: data.data.key)
            case .loading:
#if os(macOS)
                AccountTypeGuest()
#else
                nil
#endif
            case .error: AccountTypeGuest()
            }
            if let actualAccountType = accountType {
                HomeContent(accountType: actualAccountType)
            }
        }

    }
}

struct MediaClickData {
    let statusKey: MicroBlogKey
    let index: Int
    let preview: String?
}
