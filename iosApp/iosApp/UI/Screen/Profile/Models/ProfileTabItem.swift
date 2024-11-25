import SwiftUI

enum ProfileTabItem: Int, CaseIterable {
    case posts
    case replies
    case media
    case likes
    
    var title: String {
        switch self {
        case .posts:
            return "Posts"
        case .replies:
            return "Replies"
        case .media:
            return "Media"
        case .likes:
            return "Likes"
        }
    }
    
    var imageName: String {
        switch self {
        case .posts:
            return "text.alignleft"
        case .replies:
            return "arrowshape.turn.up.left.fill"
        case .media:
            return "photo.on.rectangle"
        case .likes:
            return "heart.fill"
        }
    }
}
