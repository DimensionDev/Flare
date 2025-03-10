import Combine
import Foundation
import shared

class ListDetailViewModel: BaseListViewModel {
    private let timelinePresenter: ListTimelinePresenter
    private let infoPresenter: ListInfoPresenter

    @Published var listInfo: UiList? = nil
    @Published var timelineState: ViewState<[UiTimeline]> = .loading
    @Published var isRefreshing: Bool = false
    @Published var error: Error? = nil

    init(accountType: AccountType, listId: String) {
        timelinePresenter = ListTimelinePresenter(accountType: accountType, listId: listId)
        infoPresenter = ListInfoPresenter(accountType: accountType, listId: listId)
        super.init()
        activateTimeline()
        activateInfo()
    }

    private func activateInfo() {
        let task = Task { @MainActor in
            for await state in infoPresenter.models {
                // 处理从ListInfoState获取的listInfo属性
                if let listInfoState = state as? ListInfoState {
                    switch onEnum(of: listInfoState.listInfo) {
                    case let .success(data):
                        self.listInfo = data.data
                        self.error = nil
                    case let .error(errorState):
                        // 使用NSError包装错误信息
                        let genericError = NSError(domain: "ListInfo", code: -1,
                                                   userInfo: [NSLocalizedDescriptionKey: "获取列表信息失败"])
                        self.error = handleError(genericError)
                    default:
                        break
                    }
                }
            }
        }
        addTask(task)
    }

    private func activateTimeline() {
        let task = Task { @MainActor in
            for await state in timelinePresenter.models {
                // 处理从TimelineState获取的listState属性
                if let timelineState = state as? TimelineState {
                    switch onEnum(of: timelineState.listState) {
                    case let .success(data):
                        // 将PagingState.Success转换为列表
                        var timelines: [UiTimeline] = []
                        for i in 0 ..< Int(data.itemCount) {
                            if let timeline = data.get(index: Int32(i)) {
                                timelines.append(timeline)
                            }
                        }
                        self.timelineState = timelines.isEmpty ? .empty : .loaded(timelines)
                    case .loading:
                        if case .loaded = self.timelineState {
                            // 保持当前数据，只更新加载状态
                        } else {
                            self.timelineState = .loading
                        }
                    case let .error(errorState):
                        // 创建标准错误对象，避免直接访问Kotlin异常
                        let message = "加载列表时间线失败"
                        let genericError = NSError(domain: "ListTimeline", code: -1,
                                                   userInfo: [NSLocalizedDescriptionKey: message])
                        self.timelineState = .error(genericError)
                        self.error = genericError
                    case .empty:
                        self.timelineState = .empty
                    }
                }
            }
        }
        addTask(task)
    }

    func refresh() {
        // 刷新时间线
        if let timelineState = try? timelinePresenter.models.value as? TimelineState {
            Task {
                do {
                    try await timelineState.refresh()
                } catch {
                    self.error = handleError(error)
                }
            }
        }
    }
}
