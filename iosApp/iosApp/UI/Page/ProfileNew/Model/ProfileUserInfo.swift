import Foundation
import shared

// 整合所有用户资料页需要的信息
struct ProfileUserInfo: Equatable {
    let profile: UiProfile
    let relation: UiRelation?
    let isMe: Bool
    let followCount: String
    let fansCount: String
    let fields: [String: UiRichText]
    let canSendMessage: Bool

    // 从 ProfileState 创建
    static func from(state: ProfileNewState) -> ProfileUserInfo? {
        // 只有在用户信息加载成功时才创建
        guard case let .success(user) = onEnum(of: state.userState),
              let profile = user.data as? UiProfile
        else {
            return nil
        }

        // 获取关系状态
        let relation: UiRelation? = {
            if case let .success(rel) = onEnum(of: state.relationState) {
                return rel.data
            }
            return nil
        }()

        // 获取是否是当前用户
        let isMe: Bool = {
            if case let .success(me) = onEnum(of: state.isMe) {
                return me.data as! Bool
            }
            return false
        }()

        // 获取是否可以发送消息
        let canSendMessage: Bool = {
            if case let .success(can) = onEnum(of: state.canSendMessage) {
                return can.data as! Bool
            }
            return false
        }()

        // 获取关注和粉丝数
        let followCount = profile.matrices.followsCountHumanized
        let fansCount = profile.matrices.fansCountHumanized

        // 获取用户字段信息
        let fields: [String: UiRichText] = {
            if let bottomContent = profile.bottomContent,
               let fieldsContent = bottomContent as? UiProfileBottomContentFields
            {
                return fieldsContent.fields
            }
            return [:]
        }()

        return ProfileUserInfo(
            profile: profile,
            relation: relation,
            isMe: isMe,
            followCount: followCount,
            fansCount: fansCount,
            fields: fields,
            canSendMessage: canSendMessage
        )
    }

    // MARK: - Equatable
    static func == (lhs: ProfileUserInfo, rhs: ProfileUserInfo) -> Bool {
        // 比较关键字段，避免复杂对象比较
        return lhs.profile.key.description == rhs.profile.key.description &&
               lhs.isMe == rhs.isMe &&
               lhs.followCount == rhs.followCount &&
               lhs.fansCount == rhs.fansCount &&
               lhs.canSendMessage == rhs.canSendMessage
    }
}
