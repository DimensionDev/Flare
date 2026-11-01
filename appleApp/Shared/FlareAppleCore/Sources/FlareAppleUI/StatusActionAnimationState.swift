import KotlinSharedUI
import SwiftUI
import QuartzCore

public enum StatusActionAnimationTiming {
    public static let iconSpring = Spring.snappy(duration: 0.32)
    public static let iconStartScale = 0.82
    public static let fadeDuration = 0.15
    public static let numberDuration = 0.32
    private static let numberStartControlPoint = UnitPoint(x: 0.22, y: 1)
    private static let numberEndControlPoint = UnitPoint(x: 0.36, y: 1)
    public static let numberCurve = UnitCurve.bezier(
        startControlPoint: numberStartControlPoint,
        endControlPoint: numberEndControlPoint
    )
    public static let numberTimingFunction = CAMediaTimingFunction(
        controlPoints: Float(numberStartControlPoint.x), Float(numberStartControlPoint.y),
        Float(numberEndControlPoint.x), Float(numberEndControlPoint.y)
    )
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
        color = item.color == .contentColor ? nil : item.color
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
        let iconEffect: IconEffect = icon != nil && color != previous.color
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
