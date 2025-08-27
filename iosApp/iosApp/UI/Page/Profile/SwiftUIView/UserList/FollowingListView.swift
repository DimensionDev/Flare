import shared
import SwiftUI

struct FollowingListView: View {
    let accountType: AccountType
    let userKey: MicroBlogKey
    let userName: String?

    private let presenter: FollowingPresenter

    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, userKey: MicroBlogKey, userName: String? = nil) {
        self.accountType = accountType
        self.userKey = userKey
        self.userName = userName

        
        self.presenter = UserListPresenterService.shared.getOrCreateFollowingPresenter(
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
        let followingTitle = NSLocalizedString("following_title", comment: "")
        if let userName = userName, !userName.isEmpty {
            // 英文格式：John's Following，中文格式：约翰 关注
            let currentLanguage = Locale.current.language.languageCode?.identifier ?? "en"
            if currentLanguage.hasPrefix("zh") {
                return "\(userName) \(followingTitle)"
            } else {
                return "\(userName)'s \(followingTitle)"
            }
        } else {
            return followingTitle
        }
    }
}
