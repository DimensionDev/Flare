//
//  TweetService.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/25.
//

// import Firebase
// import FirebaseFirestore
import Foundation // Needed for Date

class TweetService {
        
    static let shared = TweetService()
    
    // --- Mock Data ---
    private static func createMockTweets(users: [User]) -> [Tweet] {
        guard !users.isEmpty else { return [] }
        var tempTweets: [Tweet] = []
        let calendar = Calendar.current
        let now = Date()
        
        for i in 0..<200 {
            // Assign tweet to a random mock user
            let randomUserIndex = Int.random(in: 0..<users.count)
            let user = users[randomUserIndex]
            
            // Create a slightly varied timestamp for sorting
            let timestamp = calendar.date(byAdding: .minute, value: -i * 5, to: now) ?? now
            
            let tweet = Tweet(
                id: "mock_tweet_\(i)",
                caption: "This is mock tweet number \(i) from \(user.username). #mock #swiftui",
                timestamp: timestamp,
                uid: user.id ?? "unknown_mock_uid",
                likes: Int.random(in: 0...150),
                user: user, // Embed the user object
                didLike: nil // We'll determine this dynamically later
            )
            tempTweets.append(tweet)
        }
        // Sort by timestamp descending, like the original fetchTweets
        return tempTweets.sorted { $0.timestamp > $1.timestamp }
    }
    
    // Use UserService's mock users to create tweets
    static let mockTweets: [Tweet] = createMockTweets(users: UserService.mockUsers)
    
    // Simulate liked tweets [UserID: Set<TweetID>]
    static var mockLikedTweets: [String: Set<String>] = [
        // Let mock_user_1 like some tweets
        UserService.shared.currentMockUserId: [mockTweets[1].id!, mockTweets[5].id!, mockTweets[10].id!],
        // Let mock_user_2 like some tweets
        "mock_user_2": [mockTweets[0].id!, mockTweets[1].id!]
    ]
    // --- End Mock Data ---
    
    private init() { }
    
    // Mock uploadTweet - Does nothing, returns success
    func uploadTweet(caption: String) async throws -> Bool {
        print("DEBUG: [Mock] uploadTweet called with caption: \(caption). Simulating success.")
        // Optionally: Create a new mock tweet and add it to the static array
        // Requires making mockTweets a var and handling potential threading issues if needed.
        return true
        
        // guard let uid = Auth.auth().currentUser?.uid else { return false } // Removed
        // ... Firebase upload logic removed ...
    }
    
    // Mock fetchTweets - Returns all mock tweets
    func fetchTweets() async throws -> [Tweet] {
        print("DEBUG: [Mock] fetchTweets called.")
        // Return a copy to prevent accidental mutation if mockTweets becomes var
        var tweetsWithLikeStatus = Self.mockTweets
        // Update like status for the current user
        for i in tweetsWithLikeStatus.indices {
            tweetsWithLikeStatus[i].didLike = try? await checkIfUserLikedTweet(tweetsWithLikeStatus[i])
        }
        return tweetsWithLikeStatus
//        let snapshot = try await Firestore.firestore()... // Removed
//        return snapshot.documents.compactMap({ try? $0.data(as: Tweet.self )})
    }
    
    // Mock fetchTweets(forUid:) - Filters mock tweets by uid
    func fetchTweets(forUid uid: String) async throws -> [Tweet] {
        print("DEBUG: [Mock] fetchTweets(forUid: \(uid)) called.")
        let userTweets = Self.mockTweets.filter { $0.uid == uid }
         // Update like status for the current user
        var tweetsWithLikeStatus = userTweets
        for i in tweetsWithLikeStatus.indices {
            tweetsWithLikeStatus[i].didLike = try? await checkIfUserLikedTweet(tweetsWithLikeStatus[i])
        }
        // Already sorted by timestamp during creation
        return tweetsWithLikeStatus
//        let snapshot = try await Firestore.firestore()... // Removed
//        let tweets = snapshot.documents.compactMap({ try? $0.data(as: Tweet.self )})
//        return tweets.sorted(by: { $0.timestamp.dateValue() > $1.timestamp.dateValue() })
    }
}

// MARK: - Likes

extension TweetService {
    
    // Mock likeTweet - Updates mockLikedTweets
    func likeTweet(_ tweet: Tweet) async throws {
        let currentUserId = UserService.shared.currentMockUserId
        guard let tweetId = tweet.id else { return }
        print("DEBUG: [Mock] likeTweet called for tweet ID: \(tweetId) by user: \(currentUserId)")
        Self.mockLikedTweets[currentUserId, default: []].insert(tweetId)
        // Optional: Update the likes count in mockTweets (needs mockTweets to be var)
        // if let index = Self.mockTweets.firstIndex(where: { $0.id == tweetId }) {
        //     Self.mockTweets[index].likes += 1
        // }
        print("DEBUG: [Mock] Liked tweets state: \(Self.mockLikedTweets)")

        // guard let uid = Auth.auth().currentUser?.uid else { return } // Removed
        // ... Firebase like logic removed ...
    }
        
    // Mock unlikeTweet - Updates mockLikedTweets
    func unlikeTweet(_ tweet: Tweet) async throws {
        let currentUserId = UserService.shared.currentMockUserId
        guard let tweetId = tweet.id else { return }
         print("DEBUG: [Mock] unlikeTweet called for tweet ID: \(tweetId) by user: \(currentUserId)")
        Self.mockLikedTweets[currentUserId]?.remove(tweetId)
        // Optional: Update the likes count in mockTweets (needs mockTweets to be var)
        // if let index = Self.mockTweets.firstIndex(where: { $0.id == tweetId }) {
        //    if Self.mockTweets[index].likes > 0 {
        //        Self.mockTweets[index].likes -= 1
        //    }
        // }
        print("DEBUG: [Mock] Liked tweets state: \(Self.mockLikedTweets)")
        
        // guard let uid = Auth.auth().currentUser?.uid else { return } // Removed
        // ... Firebase unlike logic removed ...
    }
    
    // Mock checkIfUserLikedTweet - Checks mockLikedTweets
    func checkIfUserLikedTweet(_ tweet: Tweet) async throws -> Bool {
        let currentUserId = UserService.shared.currentMockUserId
        guard let tweetId = tweet.id else { return false }
        let didLike = Self.mockLikedTweets[currentUserId]?.contains(tweetId) ?? false
        print("DEBUG: [Mock] checkIfUserLikedTweet ID: \(tweetId) by user: \(currentUserId). Result: \(didLike)")
        return didLike
        
        // guard let uid = Auth.auth().currentUser?.uid, let tweetId = tweet.id else { return false } // Removed
        // ... Firebase check logic removed ...
    }
    
    // Mock fetchLikedTweets - Uses mockLikedTweets to filter mockTweets
    func fetchLikedTweets(forUid uid: String) async throws -> [Tweet] {
        print("DEBUG: [Mock] fetchLikedTweets(forUid: \(uid)) called.")
        guard let likedTweetIds = Self.mockLikedTweets[uid] else {
            print("DEBUG: [Mock] No liked tweets found for user \(uid).")
            return []
        }
        
        let likedTweets = Self.mockTweets.filter { tweet in
            likedTweetIds.contains(tweet.id ?? "")
        }
        
        // Update like status for the *queried* user (which is the current user in most cases here)
        var tweetsWithLikeStatus = likedTweets
        for i in tweetsWithLikeStatus.indices {
             tweetsWithLikeStatus[i].didLike = try? await checkIfUserLikedTweet(tweetsWithLikeStatus[i]) // Check based on current user
        }
        // Already sorted by timestamp during creation
        print("DEBUG: [Mock] Found \(tweetsWithLikeStatus.count) liked tweets for user \(uid).")
        return tweetsWithLikeStatus
        
        // var tweets = [Tweet]() // Removed
        // ... Firebase fetch liked logic removed ...
    }
}
