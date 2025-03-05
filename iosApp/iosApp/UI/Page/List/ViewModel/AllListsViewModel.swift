import Combine
import Foundation
import shared

class AllListsViewModel: BaseListViewModel {
    private let presenter: AllListPresenter
    // 保存最新的状态，以便可以调用其刷新方法
    private var latestState: AllListState? = nil

    @Published var listsState: ViewState<[UiList]> = .loading
    @Published var isRefreshing: Bool = false
    @Published var error: Error? = nil

    init(accountType: AccountType) {
        presenter = AllListPresenter(accountType: accountType)
        super.init()

        // 初始化时获取数据
        activate()
    }

    func activate() {
        let task = Task { @MainActor in
            do {
                // 使用presenter.models流获取数据
                for await listState in presenter.models {
                    // 保存最新状态
                    self.latestState = listState

                    // 更新刷新状态
                    self.isRefreshing = listState.isRefreshing

                    // 使用onEnum处理状态
                    switch onEnum(of: listState.items) {
                    case let .success(successData):
                        // 处理成功状态
                        var lists: [UiList] = []
                        for i in 0 ..< Int(successData.itemCount) {
                            if let list = successData.get(index: Int32(i)) {
                                lists.append(list)
                            }
                        }
                        self.listsState = lists.isEmpty ? .empty : .loaded(lists)
                    case .loading:
                        // 处理加载状态
                        self.listsState = .loading
                    case .error:
                        // 处理错误状态 - 创建通用错误
                        let genericError = NSError(domain: "AllLists", code: -1, userInfo: [NSLocalizedDescriptionKey: "加载列表失败"])
                        self.listsState = .error(genericError)
                        self.error = genericError
                    case .empty:
                        // 处理空状态
                        self.listsState = .empty
                    default:
                        self.listsState = .empty
                    }
                }
            } catch {
                self.listsState = .error(error)
                self.error = error
                self.isRefreshing = false
            }
        }
        addTask(task)
    }

    func refresh() {
        // 设置刷新状态
        isRefreshing = true

        // 调用状态的刷新方法
        latestState?.refresh()
    }

    func refreshAsync() async {
        refresh()
        // 等待一段时间
        try? await Task.sleep(nanoseconds: 1_000_000_000)
    }
}
