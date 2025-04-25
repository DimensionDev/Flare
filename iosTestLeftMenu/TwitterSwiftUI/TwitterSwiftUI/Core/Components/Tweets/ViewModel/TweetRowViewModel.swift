//
//  TweetRowViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/26.
//

import Foundation

class TweetRowViewModel: ObservableObject {
    
    @Published var tweet: Tweet
    
    init(tweet: Tweet) {
        self.tweet = tweet
        
        // 自分がいいねを押したかどうかを反映するためのfetch
        Task {
            try await checkIfUserLikedTweet()
        }
    }
    
    @MainActor
    func likeTweet() async throws {
        // 「♡」をタップ
        try await TweetService.shared.likeTweet(tweet)
        self.tweet.didLike = true
    }
    
    @MainActor
    func unlikeTweet() async throws {
        // 「❤︎」をタップ
        try await TweetService.shared.unlikeTweet(tweet)
        self.tweet.didLike = false
    }
    
    @MainActor
    func checkIfUserLikedTweet() async throws {
        self.tweet.didLike = try await TweetService.shared.checkIfUserLikedTweet(tweet)
    }
}
