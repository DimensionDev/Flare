//
//  TweetFilterViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import Foundation

enum ProfileTweetsTabType: Int, CaseIterable, Identifiable {
    case tweets
    case replies
    case likes
    
    var title: String {
        switch self {
        case .tweets: return "ポスト"
        case .replies: return "返信"
        case .likes: return "いいね"
        }
    }
    
    var id: Int { self.rawValue }
}
