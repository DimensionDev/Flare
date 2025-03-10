import Combine
import Foundation
import shared

class BaseListViewModel: ObservableObject {
    private var tasks: [Task<Void, Never>] = []

    func addTask(_ task: Task<Void, Never>) {
        tasks.append(task)
    }

    func cancelAllTasks() {
        tasks.forEach { $0.cancel() }
        tasks.removeAll()
    }

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
