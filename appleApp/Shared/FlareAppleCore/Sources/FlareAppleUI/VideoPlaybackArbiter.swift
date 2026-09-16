import Foundation

/// Coordinates playback across timelines and media presentations in the app.
@MainActor
public final class VideoPlaybackArbiter {
    public static let shared = VideoPlaybackArbiter()

    private struct Client {
        weak var owner: AnyObject?
        let stop: () -> Void
        let reconsider: () -> Void
        let mediaReturned: ([String], String) -> Void
        let resume: () -> Void
        let willHandoff: (String) -> Void
    }

    private var clients: [ObjectIdentifier: Client] = [:]
    private var active: ObjectIdentifier?
    private var preferred: ObjectIdentifier?
    private var presentations: [ObjectIdentifier] = []
    private var returnOwners: [ObjectIdentifier: ObjectIdentifier] = [:]

    public init() {}

    public func register(_ owner: AnyObject, stop: @escaping () -> Void, reconsider: @escaping () -> Void,
                         mediaReturned: @escaping ([String], String) -> Void = { _, _ in },
                         resume: (() -> Void)? = nil,
                         willHandoff: @escaping (String) -> Void = { _ in }) {
        clients = clients.filter { $0.value.owner != nil }
        clients[ObjectIdentifier(owner)] = Client(owner: owner, stop: stop, reconsider: reconsider,
                                                 mediaReturned: mediaReturned, resume: resume ?? reconsider,
                                                 willHandoff: willHandoff)
    }

    public func interacted(_ owner: AnyObject) {
        preferred = ObjectIdentifier(owner)
    }

    public func acquire(_ owner: AnyObject) -> Bool {
        let id = ObjectIdentifier(owner)
        if let presentation = presentations.last, presentation != id { return false }
        if active == id { return true }
        if let preferred, preferred != id, presentations.last != id { return false }
        if active != nil, preferred != id, presentations.last != id { return false }
        stopActive()
        active = id
        return true
    }

    public func settledWithoutVideo(_ owner: AnyObject) {
        let id = ObjectIdentifier(owner)
        guard presentations.last == nil || presentations.last == id else { return }
        if preferred == id || active == id { stopActive() }
    }

    public func release(_ owner: AnyObject) {
        guard active == ObjectIdentifier(owner) else { return }
        stopActive()
        reconsider()
    }

    public func withdraw(_ owner: AnyObject, mediaURLs: [String] = [], selectedMediaURL: String? = nil) {
        let id = ObjectIdentifier(owner)
        let wasTop = presentations.last == id
        let returnOwner = returnOwners.removeValue(forKey: id)
        if active == id { stopActive(continuingTo: wasTop ? selectedMediaURL : nil) }
        if wasTop, let returnOwner, let selectedMediaURL {
            clients[returnOwner]?.mediaReturned(mediaURLs, selectedMediaURL)
        }
        if preferred == id {
            preferred = returnOwner.flatMap { clients[$0]?.owner == nil ? nil : $0 }
        }
        if presentations.contains(id) {
            for child in returnOwners.keys.filter({ returnOwners[$0] == id }) {
                returnOwners[child] = returnOwner
            }
        }
        presentations.removeAll { $0 == id }
        reconsider(immediateOwner: wasTop ? returnOwner : nil)
    }

    public func present(_ owner: AnyObject, selectedMediaURL: String? = nil) {
        let id = ObjectIdentifier(owner)
        guard !presentations.contains(id) else { return }
        returnOwners[id] = preferred ?? active
        presentations.append(id)
        preferred = id
        stopActive(continuingTo: selectedMediaURL)
    }

    private func stopActive(continuingTo mediaURL: String? = nil) {
        let old = active
        active = nil
        if let old {
            if let mediaURL { clients[old]?.willHandoff(mediaURL) }
            clients[old]?.stop()
        }
    }

    private func reconsider(immediateOwner: ObjectIdentifier? = nil) {
        clients = clients.filter { $0.value.owner != nil }
        for (id, client) in Array(clients) {
            if id == immediateOwner { client.resume() } else { client.reconsider() }
        }
    }
}
