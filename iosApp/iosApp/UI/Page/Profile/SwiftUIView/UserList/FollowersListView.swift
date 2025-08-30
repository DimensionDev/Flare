import shared
import SwiftUI

struct FollowersListView: View {
    let accountType: AccountType
    let userKey: MicroBlogKey
    let userName: String?

    private let presenter: FansPresenter

    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, userKey: MicroBlogKey, userName: String? = nil) {
        self.accountType = accountType
        self.userKey = userKey
        self.userName = userName

        presenter = UserListPresenterService.shared.getOrCreateFansPresenter(
            accountType: accountType,
            userKey: userKey
        )
    }

    var body: some View {
        UserListView(presenter: presenter)
            .navigationTitle(getNavigationTitle())
            .navigationBarTitleDisplayMode(.inline)
    }

    private func getNavigationTitle() -> String {
        let fansTitle = NSLocalizedString("fans_title", comment: "")
        if let userName, !userName.isEmpty {
            // 英文格式：John's Followers，中文格式：约翰 粉丝
            let currentLanguage = Locale.current.language.languageCode?.identifier ?? "en"
            if currentLanguage.hasPrefix("zh") {
                return "\(userName) \(fansTitle)"
            } else {
                return "\(userName)'s \(fansTitle)"
            }
        } else {
            return fansTitle
        }
    }
}
