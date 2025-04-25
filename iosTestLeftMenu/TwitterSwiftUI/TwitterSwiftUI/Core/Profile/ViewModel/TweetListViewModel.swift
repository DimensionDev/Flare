//
//  TweetListViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/12.
//

import Foundation
import Firebase

class TweetListViewModel: ObservableObject {
    private let user: User
    private let tabType: ProfileTweetsTabType
    
    @Published var tweets = [Tweet]()
    @Published var likedTweets = [Tweet]()
    
    init(user: User, tabType: ProfileTweetsTabType) {
        self.user = user
        self.tabType = tabType
        
        Task {
            try await fetchTweets()
            try await fetchLikedTweets()
        }
    }
    
    @MainActor
    func fetchTweets() async throws {
        guard let uid = user.id else { return }
        let tweets = try await TweetService.shared.fetchTweets(forUid: uid)
        self.tweets = tweets
        //自分が投稿したTweetsなので自分のuserをセット
        tweets.enumerated().forEach { index, _ in
            self.tweets[index].user = self.user
        }
    }
    
    @MainActor
    func fetchLikedTweets() async throws {
        guard let uid = user.id else { return }
        let likedTweets = try await TweetService.shared.fetchLikedTweets(forUid: uid)
        self.likedTweets = likedTweets
        for index in likedTweets.indices {
            // tweetsのそれぞれUserデータをfetch
            let user = try await UserService.shared.fetchUser(withUid: likedTweets[index].uid)
            self.likedTweets[index].user = user
        }
    }
}
