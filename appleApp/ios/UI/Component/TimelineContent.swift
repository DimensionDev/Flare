import Foundation
import KotlinSharedUI

/// A native, immutable-in-use render input. Paging adaptation happens once, so
/// cell lookup, identifiers and heights always describe the same set of rows.
struct TimelineContent {
    enum Kind { case timeline, users, profileMedia }
    enum State { case unbound, loading, loaded, empty, error }
    enum Item {
        case post(UiTimelineV2)
        case user(UiProfile)
        case media(ProfileMedia)

        fileprivate var identity: ObjectIdentifier {
            switch self {
            case .post(let item): ObjectIdentifier(item)
            case .user(let item): ObjectIdentifier(item)
            case .media(let item): ObjectIdentifier(item)
            }
        }

        var id: String {
            switch self {
            case .post(let item):
                let key = item.itemKey.flatMap { $0.isEmpty ? nil : $0 } ??
                    [item.itemType, String(describing: item.accountType), String(describing: item.statusKey)].joined(separator: ":")
                return "t:" + key
            case .user(let item): return "u:\(item.key)"
            case .media(let item): return "m:\(item.key)"
            }
        }
        var renderHash: Int32 {
            switch self {
            case .post(let item): item.renderHash
            case .user(let item): Int32(truncatingIfNeeded: item.hash)
            case .media(let item): item.status.renderHash
            }
        }
        var post: UiTimelineV2? {
            if case .post(let item) = self { return item }
            return nil
        }
        var user: UiProfile? {
            if case .user(let item) = self { return item }
            return nil
        }
        var media: ProfileMedia? {
            if case .media(let item) = self { return item }
            return nil
        }
    }

    struct ItemMetadataCache {
        struct Metadata {
            // Retain the immutable input so its address cannot be reused by another item.
            fileprivate let item: Item
            let id: String
            let renderHash: Int32
        }

        private(set) var entries: [ObjectIdentifier: Metadata] = [:]

        mutating func update(_ items: [Item?]) -> [Metadata?] {
            var next: [ObjectIdentifier: Metadata] = .init(minimumCapacity: items.count)
            let result = items.map { item -> Metadata? in
                guard let item else { return nil }
                let identity = item.identity
                let metadata = entries[identity] ?? Metadata(item: item, id: item.id, renderHash: item.renderHash)
                next[identity] = metadata
                return metadata
            }
            entries = next
            return result
        }
    }
    struct Failure {
        let error: KotlinThrowable
        let retry: () -> Void
    }
    enum Footer { case none, loading, end, error }

    var kind: Kind = .timeline
    var state: State = .unbound
    var items: [Item?] = []
    var header: UiState<UiTimelineV2>?
    var key: AnyHashable?
    var isRefreshing = false
    var footer: Footer = .none
    var failure: Failure?
    var appendFailure: Failure?
    var access: (Int) -> Void = { _ in }

    var isInitialLoading: Bool {
        if state == .loading { return true }
        if state == .unbound, let header, case .loading = onEnum(of: header) { return true }
        return false
    }
    var hasPosts: Bool { kind == .timeline && (state == .loaded || header != nil) }

    static func timeline(_ data: PagingState<UiTimelineV2>?, header: UiState<UiTimelineV2>?, key: AnyHashable?) -> Self {
        var content = adapt(data, kind: .timeline, item: Item.post)
        content.header = header
        content.key = key
        return content
    }
    static func users(_ data: PagingState<UiProfile>) -> Self { adapt(data, kind: .users, item: Item.user) }
    static func profileMedia(_ data: PagingState<ProfileMedia>) -> Self { adapt(data, kind: .profileMedia, item: Item.media) }

    private static func adapt<Value: AnyObject>(_ data: PagingState<Value>?, kind: Kind, item: (Value) -> Item) -> Self {
        var content = Self(kind: kind)
        guard let data else { return content }
        switch onEnum(of: data) {
        case .loading: content.state = .loading
        case .empty: content.state = .empty
        case .error(let error):
            content.state = .error
            content.failure = Failure(error: error.error, retry: { error.onRetry() })
        case .success(let success):
            content.state = .loaded
            content.isRefreshing = success.isRefreshing
            content.items = (0..<Int(success.itemCount)).map { index in
                success.peek(index: Int32(index)).map(item)
            }
            content.access = { index in
                guard index >= 0, index < Int(success.itemCount) else { return }
                _ = success.get(index: Int32(index))
            }
            switch onEnum(of: success.appendState) {
            case .loading: content.footer = .loading
            case .notLoading(let state): content.footer = state.endOfPaginationReached ? .end : .none
            case .error(let error):
                content.footer = .error
                content.appendFailure = Failure(error: error.error, retry: { success.retry() })
            }
        }
        return content
    }
}
