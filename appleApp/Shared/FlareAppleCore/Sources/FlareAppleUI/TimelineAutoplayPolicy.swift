import Foundation

/// Selection is separate from visibility: a selected item can start at 60%,
/// while the current player can continue until its last visible pixel leaves.
public struct TimelineAutoplayPolicy {
    public struct Candidate: Equatable {
        public let id: String
        public let groupID: String?
        public let isVisible: Bool
        public let isSelected: Bool
        public let canStart: Bool
        public let distance: Double
        public let mediaURL: String?

        public init(id: String, groupID: String? = nil, isVisible: Bool, isSelected: Bool = true,
                    canStart: Bool, distance: Double, mediaURL: String? = nil) {
            self.id = id
            self.groupID = groupID
            self.isVisible = isVisible
            self.isSelected = isSelected
            self.canStart = canStart
            self.distance = distance
            self.mediaURL = mediaURL
        }
    }

    public private(set) var activeID: String?
    private var preferredGroupID: String?
    private var preferredMediaURL: String?

    public init() {}

    public mutating func verticalScrollBegan() {
        preferredGroupID = nil
        preferredMediaURL = nil
    }

    public mutating func interactedWithCarousel(_ groupID: String) {
        preferredGroupID = groupID
        preferredMediaURL = nil
    }

    public mutating func returnedToMedia(groupID: String, mediaURL: String) {
        preferredGroupID = groupID
        preferredMediaURL = mediaURL
    }

    private func matchesSelection(_ candidate: Candidate) -> Bool {
        preferredMediaURL.map { candidate.mediaURL == $0 } ?? candidate.isSelected
    }

    @discardableResult
    public mutating func select(from candidates: [Candidate], isScrolling: Bool) -> String? {
        let current = candidates.first { $0.id == activeID && $0.isVisible }
        if isScrolling {
            activeID = current?.id
        } else if let preferredGroupID {
            // A selected image leaves this group without a video candidate. Keep
            // the timeline quiet until another interaction changes the preference.
            if let current, current.groupID == preferredGroupID, matchesSelection(current) {
                activeID = current.id
            } else {
                activeID = candidates.first {
                    $0.groupID == preferredGroupID && $0.isVisible && matchesSelection($0) && $0.canStart
                }?.id
            }
        } else if let current {
            activeID = current.id
        } else {
            activeID = candidates.filter { $0.isVisible && $0.isSelected && $0.canStart }
                .min { $0.distance < $1.distance }?.id
        }
        return activeID
    }
}
