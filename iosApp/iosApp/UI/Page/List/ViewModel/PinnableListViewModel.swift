import Combine
import Foundation
import shared

//class PinnableListViewModel: BaseListViewModel {
//    private let presenter: PinnableListPresenter
//    let accountType: AccountType
//
//    @Published var listsState: ViewState<[UiList]> = .loading
//    @Published var isRefreshing: Bool = false
//    @Published var error: Error? = nil
//
//    init(accountType: AccountType) {
//        presenter = PinnableListPresenter(accountType: accountType)
//        self.accountType = accountType
//        super.init()
//
//        // 初始化时获取数据
//        activate()
//    }
//
//    func activate() {
//        let task = Task { @MainActor in
//            do {
//                // 使用presenter.models流获取数据
//                for await pinnableState in presenter.models {
//                    // 使用onEnum处理状态
//                    let state = pinnableState.items
//                    if case let .success(data) = onEnum(of: state) {
//                        // 处理成功状态，将ImmutableList转换为Swift数组
//                        var lists: [UiList] = []
//                        for i in 0 ..< Int(data.data.count) {
//                            if let list = data.data[i] as? UiList {
//                                lists.append(list)
//                            }
//                        }
//                        self.listsState = lists.isEmpty ? .empty : .loaded(lists)
//                    } else if case .loading = onEnum(of: state) {
//                        // 处理加载状态
//                        self.listsState = .loading
//                    } else if case .error = onEnum(of: state) {
//                        // 处理错误状态 - 创建通用错误
//                        let genericError = NSError(domain: "PinnableLists", code: -1, userInfo: [NSLocalizedDescriptionKey: "加载可固定列表失败"])
//                        self.listsState = .error(genericError)
//                        self.error = genericError
//                    } else {
//                        // 默认为空状态
//                        self.listsState = .empty
//                    }
//                }
//            } catch {
//                self.listsState = .error(error)
//                self.error = error
//                self.isRefreshing = false
//            }
//        }
//        addTask(task)
//    }
//
//    func refresh() {
//        // 仅设置刷新状态，因为PinnableListState没有refresh方法
//        isRefreshing = true
//
//        // 重新激活以获取新数据
//        activate()
//    }
//}
