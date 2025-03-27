import Combine
import os.log
import shared
import SwiftUI
import UIKit

 class ComposeManager: ObservableObject {
    static let shared = ComposeManager()
    
    @Published var showCompose = false
    @Published var composeAccountType: AccountType?
    @Published var composeStatus: FlareComposeStatus?
    
    private init() {}
    
    func showNewCompose(accountType: AccountType) {
        composeAccountType = accountType
        composeStatus = nil
        showCompose = true
    }
    
    func showReply(accountType: AccountType, statusKey: MicroBlogKey) {
        composeAccountType = accountType
        composeStatus = .reply(statusKey: statusKey)
        showCompose = true
    }
    
    func showQuote(accountType: AccountType, statusKey: MicroBlogKey) {
        composeAccountType = accountType
        composeStatus = .quote(statusKey: statusKey)
        showCompose = true
    }
    
    func showVVOComment(accountType: AccountType, statusKey: MicroBlogKey, rootId: String) {
        composeAccountType = accountType
        composeStatus = .vvoComment(statusKey: statusKey, rootId: rootId)
        showCompose = true
    }
    
    func dismiss() {
        showCompose = false
    }
}
