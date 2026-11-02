import KotlinSharedUI
import SwiftUI

public enum StatusActionAnimationTiming {
    // Android uses stiffness 400 and damping ratio 0.5 for scale-in, and critical damping for fade/scale-out.
    public static let iconSpring = Spring(mass: 1, stiffness: 400, damping: 20)
    public static let iconStartScale = 0.0
    public static let numberSpring = iconSpring
    public static let fadeSpring = Spring(mass: 1, stiffness: 400, damping: 40)
}

public struct StatusActionAnimationState: Equatable {
    public struct Identity: Hashable {
        private let postKey: String
        private let actionKey: String
        private let family: PostActionFamily?
        private let position: Int

        public init?(postKey: String?, item: ActionMenu.Item, position: Int) {
            guard let postKey, !postKey.isEmpty else { return nil }
            if !item.updateKey.isEmpty {
                actionKey = "key:\(item.updateKey)"
            } else if let family = item.actionFamily {
                actionKey = "family:\(family.name)"
            } else {
                return nil
            }
            self.postKey = postKey
            family = item.actionFamily
            self.position = position
        }
    }

    public enum IconEffect: Equatable { case none, pop, fade }
    public enum NumberEffect: Equatable { case none, up, down }

    public struct Effects {
        public let icon: IconEffect
        public let number: NumberEffect
    }

    public let identity: Identity?
    public let icon: UiIcon?
    public let color: ActionMenu.ItemColor?
    public let count: Int64?
    public let text: String?
    private let enabled: Bool

    public init(identity: Identity?, item: ActionMenu.Item, showNumbers: Bool) {
        self.identity = identity
        icon = item.icon
        color = item.color
        count = showNumbers ? item.count?.value : nil
        let countText = showNumbers ? item.count?.humanized : nil
        text = countText?.isEmpty == false ? countText : nil
        enabled = item.enabled
    }

    public func effects(from previous: Self?, isVisible: Bool, reduceMotion: Bool) -> Effects {
        guard isVisible, !reduceMotion, enabled,
              let identity, let previous, identity == previous.identity else {
            return Effects(icon: .none, number: .none)
        }
        let color = self.color == .contentColor ? nil : self.color
        let previousColor = previous.color == .contentColor ? nil : previous.color
        let iconEffect: IconEffect = icon != nil && color != previousColor
            ? (color == nil ? .fade : .pop) : .none
        let numberEffect: NumberEffect
        if let count, let oldCount = previous.count, text != nil, previous.text != nil,
           text != previous.text, count != oldCount {
            numberEffect = count > oldCount ? .up : .down
        } else {
            numberEffect = .none
        }
        return Effects(icon: iconEffect, number: numberEffect)
    }
}
