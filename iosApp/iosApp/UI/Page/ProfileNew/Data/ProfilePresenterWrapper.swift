import Foundation
import os.log
import shared
import SwiftUI

class ProfilePresenterWrapper: ObservableObject {
   
    let presenter: ProfileNewPresenter
    @Published var isShowAppBar: Bool? = nil // nil: 初始状态, true: 显示, false: 隐藏
 
     
    init(accountType: AccountType, userKey: MicroBlogKey?) {
        os_log("[📔][ProfilePresenterWrapper - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")

        presenter = .init(accountType: accountType, userKey: userKey)

        
        isShowAppBar = nil
     }

   
    func updateNavigationState(showAppBar: Bool?) {
        os_log("[📔][ProfilePresenterWrapper]更新导航栏状态: showAppBar=%{public}@", log: .default, type: .debug, String(describing: showAppBar))
        Task { @MainActor in
            isShowAppBar = showAppBar
        }
    }
}
