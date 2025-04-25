//
//  FeedTabListViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/13.
//

import Foundation
import Firebase

class FeedTabListViewModel: ObservableObject {
    
    let selectedFilter: FeedTabFilter
    
    @Published var tweets = [Tweet]()
    @Published var tweetsOfFollowing = [Tweet]()
    
    @Published var likedTweets = [Tweet]()
    
    init(selectedFilter: FeedTabFilter) {
        self.selectedFilter = selectedFilter
        
        switch selectedFilter {
        case .recommend:
            Task {
                try await self.fetchTweets()
            }
        case .following:
            Task {
                try await self.fetchTweetsFollowings()
            }
        }
    }
    
    @MainActor
    func refreshed() {
        Task {
            switch selectedFilter {
            case .recommend:
                try await fetchTweets()
            case .following:
                try await fetchTweetsFollowings()
            }
        }
    }
    
    @MainActor
    func fetchTweets() async throws {
        
        let tweets = try await TweetService.shared.fetchTweets()
        //いったん画面に表示
        self.tweets = tweets
        
        for index in tweets.indices {
            //直後にそれぞれUserデータを取得して画面表示
            self.tweets[index].user = try await UserService.shared.fetchUser(withUid: tweets[index].uid)
        }
    }
    
    @MainActor
    func fetchTweetsFollowings() async throws {
        
        let followingUserIds = try await UserService.shared.fetchFollowingUserIds()
        
        var tweetList = [Tweet]()
        
        for id in followingUserIds {
            let tweets = try await TweetService.shared.fetchTweets(forUid: id)
            tweetList.append(contentsOf: tweets)
        }
        
//        self.tweetsOfFollowing = tweetList.sorted(by: { $0.timestamp.dateValue() > $1.timestamp.dateValue() })
        
        for index in tweetsOfFollowing.indices {
            let uid = tweetsOfFollowing[index].uid
            tweetsOfFollowing[index].user = try await UserService.shared.fetchUser(withUid: uid)
        }
    }

//    @MainActor
//    func fetchLikedTweets() async throws {
//        guard let uid = AuthService.shared.currentUser?.id else { return }
//        let likedTweets = try await TweetService.shared.fetchLikedTweets(forUid: uid)
//        self.likedTweets = likedTweets
//        for index in likedTweets.indices {
//            // tweetsのそれぞれUserデータをfetch
//            let user = try await UserService.shared.fetchUser(withUid: likedTweets[index].uid)
//            self.likedTweets[index].user = user
//        }
//    }
}
