import Combine
import Foundation
import shared

class ListMembersViewModel: BaseListViewModel {
    private let presenter: ListMembersPresenter
    let accountType: AccountType

    @Published var membersState: ViewState<[UiUserV2]> = .loading
    @Published var error: Error? = nil

    init(accountType: AccountType, listId: String) {
        presenter = ListMembersPresenter(accountType: accountType, listId: listId)
        self.accountType = accountType
        super.init()
        activate()
    }

    func activate() {
        let task = Task { @MainActor in
            do {
                // 使用presenter.models流获取数据
                for await memberState in presenter.models {
                    // 使用onEnum处理PagingState
                    switch onEnum(of: memberState.memberInfo) {
                    case let .success(successData):
                        // 处理成功状态
                        var members: [UiUserV2] = []
                        for i in 0 ..< Int(successData.itemCount) {
                            if let member = successData.get(index: Int32(i)) {
                                members.append(member)
                            }
                        }
                        self.membersState = members.isEmpty ? .empty : .loaded(members)
                    case .loading:
                        // 处理加载状态
                        self.membersState = .loading
                    case .error:
                        // 处理错误状态 - 创建通用错误
                        let genericError = NSError(domain: "ListMembers", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to load list members"])
                        self.membersState = .error(genericError)
                        self.error = handleError(genericError)
                    case .empty:
                        // 处理空状态
                        self.membersState = .empty
                    default:
                        self.membersState = .empty
                    }
                }
            } catch {
                self.membersState = .error(error)
                self.error = handleError(error)
            }
        }
        addTask(task)
    }

    func refresh() {
        // 在需要刷新时重新激活
        activate()
    }
}
