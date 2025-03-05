import Combine
import Foundation
import shared

/// 列表功能基础视图模型，处理任务生命周期和错误处理
class BaseListViewModel: ObservableObject {
    private var tasks: [Task<Void, Never>] = []

    /// 添加任务到管理列表
    func addTask(_ task: Task<Void, Never>) {
        tasks.append(task)
    }

    /// 取消所有任务
    func cancelAllTasks() {
        tasks.forEach { $0.cancel() }
        tasks.removeAll()
    }

    /// 处理错误转换
    func handleError(_ error: Error) -> ListError {
        if let urlError = error as? URLError {
            .network(urlError)
        } else if error.localizedDescription.contains("HTTP") {
            .api(error)
        } else {
            .unknown
        }
    }

    deinit {
        cancelAllTasks()
    }
}
