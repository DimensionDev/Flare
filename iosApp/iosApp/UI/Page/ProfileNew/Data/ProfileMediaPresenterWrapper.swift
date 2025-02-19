import Foundation
import os.log
import shared
import SwiftUI

class ProfileMediaPresenterWrapper: ObservableObject {
    // - Properties
    let presenter: ProfileMediaPresenter

    // - Init
    init(accountType: AccountType, userKey: MicroBlogKey?) {
        os_log("[📔][ProfileMediaPresenterWrapper - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")
        presenter = .init(accountType: accountType, userKey: userKey)
    }

}
