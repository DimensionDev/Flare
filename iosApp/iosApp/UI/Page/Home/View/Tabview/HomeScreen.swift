import Awesome
import shared
import SwiftUI

struct HomeScreen: View {
    @State private var presenter = ActiveAccountPresenter()

    var body: some View {
        ObservePresenter(presenter: presenter) { userState in
            let accountType: AccountType? = switch onEnum(of: userState.user) {
            case let .success(data): AccountTypeSpecific(accountKey: data.data.key)
            case .loading:
                #if os(macOS)
                    AccountTypeGuest()
                #else
                    nil
                #endif
            case .error: AccountTypeGuest()
            }

            if let actualAccountType = accountType {
                HomeTabViewContent(accountType: actualAccountType)
            }
        }
    }
}
