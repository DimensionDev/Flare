import Foundation
import shared

 
struct ProfileUserInfo: Equatable {
    let profile: UiProfile
    let relation: UiRelation?
    let isMe: Bool
    let followCount: String
    let fansCount: String
    let fields: [String: UiRichText]
    let canSendMessage: Bool

  
    static func from(state: ProfileNewState) -> ProfileUserInfo? {
     
        guard case let .success(user) = onEnum(of: state.userState),
              let profile = user.data as? UiProfile
        else {
            return nil
        }

       
        let relation: UiRelation? = {
            if case let .success(rel) = onEnum(of: state.relationState) {
                return rel.data
            }
            return nil
        }()

         let isMe: Bool = {
            if case let .success(me) = onEnum(of: state.isMe) {
                return me.data as! Bool
            }
            return false
        }()

         let canSendMessage: Bool = {
            if case let .success(can) = onEnum(of: state.canSendMessage) {
                return can.data as! Bool
            }
            return false
        }()

         let followCount = profile.matrices.followsCountHumanized
        let fansCount = profile.matrices.fansCountHumanized

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
 

    static func == (lhs: ProfileUserInfo, rhs: ProfileUserInfo) -> Bool {
      
        lhs.profile.key.description == rhs.profile.key.description &&
            lhs.isMe == rhs.isMe &&
            lhs.followCount == rhs.followCount &&
            lhs.fansCount == rhs.fansCount &&
            lhs.canSendMessage == rhs.canSendMessage
    }
}
