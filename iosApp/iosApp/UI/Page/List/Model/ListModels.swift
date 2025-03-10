import Foundation
import shared

/// 视图状态枚举，用于管理UI状态
enum ViewState<T> {
    case loading
    case loaded(T)
    case empty
    case error(Error)

    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }

    var value: T? {
        if case let .loaded(value) = self { return value }
        return nil
    }

    var error: Error? {
        if case let .error(error) = self { return error }
        return nil
    }

    var isEmpty: Bool {
        if case .empty = self { return true }
        return false
    }
}

/// 自定义错误类型
enum ListError: LocalizedError {
    case network(Error)
    case api(Error)
    case unknown

    var errorDescription: String? {
        switch self {
        case .network:
            "网络连接问题，请检查您的网络并重试"
        case let .api(error):
            "服务器错误: \(error.localizedDescription)"
        case .unknown:
            "发生未知错误，请稍后再试"
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .network:
            "请检查您的网络连接或稍后再试"
        case .api:
            "请稍后再试或联系客服"
        case .unknown:
            "请尝试重启应用或联系客服"
        }
    }
}
