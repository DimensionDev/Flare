import SwiftUI
import KotlinSharedUI

public extension EnvironmentValues {
    @Entry var shareImageLoading: ShareImageLoading? = nil
}

@MainActor
public final class ShareImageLoading {
    private enum State { case loading, ready, failed }
    private var images: [UUID: State] = [:]
    private var removed: Set<UUID> = []

    public init() {}

    func register(_ key: UUID) {
        removed.remove(key)
        if images[key] == nil { images[key] = .loading }
    }
    func complete(_ key: UUID, success: Bool) {
        if !removed.contains(key) { images[key] = success ? .ready : .failed }
    }
    func remove(_ key: UUID) {
        removed.insert(key)
        images.removeValue(forKey: key)
    }

    public func awaitReady() async throws {
        let deadline = ContinuousClock.now.advanced(by: .seconds(15))
        while images.values.contains(.loading) {
            guard ContinuousClock.now < deadline else { throw StatusShareRenderError.mediaUnavailable }
            try await Task.sleep(for: .milliseconds(50))
        }
        try Task.checkCancellation()
        guard !images.values.contains(.failed) else { throw StatusShareRenderError.mediaUnavailable }
    }

    public static func checkSize(_ size: CGSize, scale: CGFloat) throws {
        let width = (size.width * scale).rounded(.up)
        let height = (size.height * scale).rounded(.up)
        guard width.isFinite, height.isFinite,
              width > 0, height > 0, width <= 16_384, height <= 16_384,
              StatusShareImageLimits.shared.canRender(width: Int32(width), height: Int32(height)) else {
            throw StatusShareRenderError.tooLarge
        }
    }
}

public enum StatusShareRenderError: LocalizedError {
    case tooLarge
    case mediaUnavailable

    public var errorDescription: String? {
        switch self {
        case .tooLarge: String(localized: "share_replies_too_large", bundle: .main)
        case .mediaUnavailable: String(localized: "share_replies_render_failed", bundle: .main)
        }
    }
}
