import Foundation
import shared

enum FlareError: Error, Equatable, LocalizedError {
    /// 网络错误
    case network(NetworkError)
    /// 数据错误
    case data(DataError)
    /// 认证错误
    case authentication(AuthError)
    /// 业务逻辑错误
    case business(BusinessError)
    /// 系统错误
    case system(SystemError)
    /// 未知错误
    case unknown(String)

    enum NetworkError: Equatable {
        case noConnection
        case timeout
        case serverError(Int)
        // 🗑️ Removed: rateLimited, badRequest, unauthorized, forbidden, notFound, serverUnavailable
        // These specific error types were never used in practice
    }

    enum DataError: Equatable {
        case parsing
        case corruption
        // 🗑️ Removed: notFound, invalidFormat, cacheExpired, inconsistentState
        // These specific error types were never used in practice
    }

    enum AuthError: Equatable {
        case unauthorized
        // 🗑️ Removed: tokenExpired, accountSuspended, invalidCredentials, accountNotFound, permissionDenied
        // These specific error types were never used in practice
    }

    enum BusinessError: Equatable {
        case timelineNotFound
        case userNotFound
        case postNotFound
        case operationNotAllowed
        case quotaExceeded
        case contentBlocked
    }

    enum SystemError: Equatable {
        case memoryWarning
        case diskSpaceLow
        case backgroundTaskExpired
        case appVersionTooOld
        case deviceNotSupported
    }

    var errorDescription: String? {
        switch self {
        case let .network(networkError):
            networkError.description
        case let .data(dataError):
            dataError.description
        case let .authentication(authError):
            authError.description
        case let .business(businessError):
            businessError.description
        case let .system(systemError):
            systemError.description
        case let .unknown(message):
            "Unknown error: \(message)"
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .network(.noConnection):
            "Please check your internet connection and try again."
        case .network(.timeout):
            "Request timed out. Please try again."
        case .network(.serverError):
            "Server error occurred. Please try again later."
        case .authentication(.unauthorized):
            "Please log in again."
        case .data(.parsing), .data(.corruption):
            "Data error occurred. Please refresh and try again."
        case .business(.timelineNotFound):
            "Timeline not found. Please refresh and try again."
        case .system(.memoryWarning):
            "Low memory. Please close other apps and try again."
        default:
            "Please try again or contact support if the problem persists."
        }
    }

    var failureReason: String? {
        switch self {
        case .network(.noConnection):
            "No internet connection available"
        case .network(.timeout):
            "Network request timed out"
        case let .network(.serverError(code)):
            "Server returned error code \(code)"
        // 🗑️ Removed reference to deleted .tokenExpired case
        case .data(.parsing):
            "Failed to parse server response"
        case .business(.operationNotAllowed):
            "Operation is not allowed in current context"
        default:
            nil
        }
    }

    // MARK: - 错误分类

    /// 是否为可重试错误
    var isRetryable: Bool {
        switch self {
        case .network(.timeout), .network(.serverError):
            true
        default:
            false
        }
    }

    /// 是否需要用户认证
    var requiresAuthentication: Bool {
        switch self {
        case .authentication:
            true
        // 🗑️ Removed reference to deleted .unauthorized case
        default:
            false
        }
    }

    /// 是否为致命错误
    var isFatal: Bool {
        switch self {
        // 🗑️ Removed references to deleted error cases
        default:
            false
        }
    }

    /// 错误严重级别
    var severity: ErrorSeverity {
        switch self {
        case .network(.noConnection), .authentication(.unauthorized):
            .medium
        case .data(.parsing):
            .low
        default:
            .medium
        }
    }

    // MARK: - 从KMP错误转换

    /// 从KMP的Throwable创建FlareError
    /// - Parameter throwable: KMP的错误对象
    /// - Returns: 转换后的FlareError
    static func from(_ throwable: KotlinThrowable) -> FlareError {
        let message = throwable.message ?? "Unknown error"
        let lowercaseMessage = message.lowercased()

        // 网络错误分类
        if lowercaseMessage.contains("network") || lowercaseMessage.contains("connection") {
            if lowercaseMessage.contains("timeout") {
                return .network(.timeout)
            } else {
                return .network(.noConnection)
            }
        }

        // HTTP状态码错误
        if lowercaseMessage.contains("401") || lowercaseMessage.contains("unauthorized") {
            return .authentication(.unauthorized)
        }
        // 🗑️ Removed references to deleted error cases: forbidden, notFound, rateLimited
        // These are now handled by the generic serverError case
        if lowercaseMessage.contains("500") || lowercaseMessage.contains("server error") {
            return .network(.serverError(500))
        }

        // 数据错误分类
        if lowercaseMessage.contains("parsing") || lowercaseMessage.contains("json") {
            return .data(.parsing)
        }
        if lowercaseMessage.contains("corruption") || lowercaseMessage.contains("corrupt") {
            return .data(.corruption)
        }

        // 🗑️ Removed references to deleted auth error cases: tokenExpired, accountSuspended
        // These are now handled by the generic unauthorized case

        // 业务错误分类
        if lowercaseMessage.contains("timeline") {
            return .business(.timelineNotFound)
        }
        if lowercaseMessage.contains("user"), lowercaseMessage.contains("not found") {
            return .business(.userNotFound)
        }

        // 默认返回未知错误
        return .unknown(message)
    }

    /// 从Swift Error创建FlareError
    /// - Parameter error: Swift错误对象
    /// - Returns: 转换后的FlareError
    static func from(_ error: Error) -> FlareError {
        if let flareError = error as? FlareError {
            return flareError
        }

        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost:
                return .network(.noConnection)
            case .timedOut:
                return .network(.timeout)
            case .badServerResponse:
                return .network(.serverError(500))
            default:
                return .network(.serverError(0))
            }
        }

        return .unknown(error.localizedDescription)
    }
}

// MARK: - ErrorSeverity

/// 错误严重级别
enum ErrorSeverity: Int, CaseIterable {
    case low = 1
    case medium = 2
    case high = 3
    case critical = 4

    var description: String {
        switch self {
        case .low: "Low"
        case .medium: "Medium"
        case .high: "High"
        case .critical: "Critical"
        }
    }
}

// MARK: - 错误描述扩展

private extension FlareError.NetworkError {
    var description: String {
        switch self {
        case .noConnection:
            "No internet connection"
        case .timeout:
            "Request timeout"
        case let .serverError(code):
            "Server error (\(code))"
            // 🗑️ Removed descriptions for deleted error cases
        }
    }
}

private extension FlareError.DataError {
    var description: String {
        switch self {
        case .parsing:
            "Data parsing error"
        case .corruption:
            "Data corruption"
            // 🗑️ Removed descriptions for deleted data error cases
        }
    }
}

private extension FlareError.AuthError {
    var description: String {
        switch self {
        case .unauthorized:
            "Unauthorized access"
            // 🗑️ Removed descriptions for deleted auth error cases
        }
    }
}

private extension FlareError.BusinessError {
    var description: String {
        switch self {
        case .timelineNotFound:
            "Timeline not found"
        case .userNotFound:
            "User not found"
        case .postNotFound:
            "Post not found"
        case .operationNotAllowed:
            "Operation not allowed"
        case .quotaExceeded:
            "Quota exceeded"
        case .contentBlocked:
            "Content blocked"
        }
    }
}

private extension FlareError.SystemError {
    var description: String {
        switch self {
        case .memoryWarning:
            "Low memory warning"
        case .diskSpaceLow:
            "Low disk space"
        case .backgroundTaskExpired:
            "Background task expired"
        case .appVersionTooOld:
            "App version too old"
        case .deviceNotSupported:
            "Device not supported"
        }
    }
}
