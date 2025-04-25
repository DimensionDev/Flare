//
//  UserService.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

// import Firebase
// import FirebaseFirestore
import Combine
import SwiftUI // UIImage is used in updateProfile
// import UIKit // Import UIKit for UIImage - Removed


class UserService {
    
    static let shared = UserService()
    let currentMockUserId = "mock_user_1"

    // --- Move Mock Data Inside --- 
    private static func createMockUsers() -> [User] {
        var tempUsers: [User] = []
        for i in 1...10 {
            let user = User(
                id: "mock_user_\(i)",
                username: "MockUser\(i)",
                profileImageUrl: "https://picsum.photos/seed/\(i)/200",
                profileHeaderImageUrl: "https://picsum.photos/seed/header\(i)/600/200",
                email: "mockuser\(i)@example.com",
                bio: i == 1 ? "This is the Mock Current User." : "Bio for Mock User \(i)",
                location: "Location \(i)",
                webUrl: i % 3 == 0 ? "https://mock-web-\(i).com" : nil,
                isCurrentUserOverride: i == 1
            )
            tempUsers.append(user)
        }
        return tempUsers
    }
    
    static let mockUsers: [User] = createMockUsers()
    
    // Simulate following relationships
    static var mockFollowing: [String: Set<String>] = [
        "mock_user_1": ["mock_user_2", "mock_user_3", "mock_user_5"], // User 1 follows 2, 3, 5
        "mock_user_2": ["mock_user_1"],
        "mock_user_4": ["mock_user_1"]
    ]
    
    static var mockFollowers: [String: Set<String>] = [
        "mock_user_1": ["mock_user_2", "mock_user_4"], // User 1 is followed by 2, 4
        "mock_user_2": ["mock_user_1"],
        "mock_user_3": ["mock_user_1"],
        "mock_user_5": ["mock_user_1"]
    ]
    // --- End Mock Data ---

    private init() { }
    
    @MainActor
    func fetchUser(withUid uid: String) async throws -> User? {
        print("DEBUG: [Mock] Fetching user with UID: \(uid)")
        // Find user in mock data
        return UserService.mockUsers.first { $0.id == uid } // Use static member
    }
    
    func fetchUsers() async throws -> [User] {
        print("DEBUG: [Mock] Fetching all users")
        return UserService.mockUsers // Use static member
    }
    
    // ヒットしたユーザーリストを取得
    func fetchUsers(with query: String) async throws -> [User] {
        print("DEBUG: [Mock] Fetching users with query: \(query)")
        guard !query.isEmpty else { return [] }
        
        let lowercasedQuery = query.lowercased()
        
        // Simple filtering based on username or email
        let filteredUsers = UserService.mockUsers.filter { user in // Use static member
            user.username.lowercased().contains(lowercasedQuery) ||
            user.email.lowercased().contains(lowercasedQuery)
        }
        return filteredUsers
    }
    
    // Remove UIImage parameters to avoid UIKit dependency in mock
    func updateProfile(name: String?,
                       bio: String?,
                       location: String?,
                       webUrl: String?) async throws {
        // In a mock environment, we often just simulate success without persistent changes
        // Or we could update the mockUsers array if needed, but that adds complexity.
        print("DEBUG: [Mock] updateProfile called. Simulating success.")
        // Optional: Find current user in mockUsers and update properties if needed for testing flows
        // guard let index = mockUsers.firstIndex(where: { $0.id == currentMockUserId }) else { return }
        // mockUsers[index].username = name ?? mockUsers[index].username // Example update (needs User to be mutable or re-creation)
        try await AuthService.shared.refreshCurrentUser() // Still call this to mimic flow
    }
    
    // Helper functions are likely unused now or need modification if profile update modifies mock data
    // private func tryUploadImage(...) async throws { ... }
    // private func addIfNotEmpty(...) { ... }
}


//MARK: - Following & Followers

extension UserService {
    
    
    func fetchFollowingCount(with uid: String) async throws -> Int {
        print("DEBUG: [Mock] Fetching following count for UID: \(uid)")
        return UserService.mockFollowing[uid]?.count ?? 0
    }
    
    
    func fetchFollowersCount(with uid: String) async throws -> Int {
        print("DEBUG: [Mock] Fetching followers count for UID: \(uid)")
        return UserService.mockFollowers[uid]?.count ?? 0
    }
    
    func fetchFollowers(with uid: String) async throws -> [User] {
        print("DEBUG: [Mock] Fetching followers for UID: \(uid)")
        guard let followerIds = UserService.mockFollowers[uid] else { return [] }
        
        let followers = UserService.mockUsers.filter { user in
            followerIds.contains(user.id ?? "")
        }
        return followers
    }
    
    func fetchFollowingUserIds() async throws -> [String] {
         print("DEBUG: [Mock] Fetching following user IDs for current user")
        return Array(UserService.mockFollowing[currentMockUserId] ?? [])
    }
    
    func fetchFollowing(with uid: String) async throws -> [User] {
        print("DEBUG: [Mock] Fetching following for UID: \(uid)")
        guard let followingIds = UserService.mockFollowing[uid] else { return [] }
        
        let followingUsers = UserService.mockUsers.filter { user in
            followingIds.contains(user.id ?? "")
        }
        return followingUsers
    }
    
    func checkIfUserIsFollowing(for uid: String) async throws -> Bool {
        print("DEBUG: [Mock] Checking if current user follows UID: \(uid)")
        let isFollowing = UserService.mockFollowing[currentMockUserId]?.contains(uid) ?? false
        return isFollowing
    }
    
    func followUser(uid: String) async throws -> Bool {
        print("DEBUG: [Mock] Following user UID: \(uid)")
        // Simulate adding follow relationship
        UserService.mockFollowing[currentMockUserId, default: []].insert(uid)
        UserService.mockFollowers[uid, default: []].insert(currentMockUserId)
        // In a real mock, you might want to update the user's isFollowed state if it exists
        // and trigger UI updates. For now, just return success.
        print("DEBUG: [Mock] Following state after follow: \(UserService.mockFollowing)")
        print("DEBUG: [Mock] Followers state after follow: \(UserService.mockFollowers)")
        return true
    }
    
    func unfollowUser(uid: String) async throws -> Bool {
        print("DEBUG: [Mock] Unfollowing user UID: \(uid)")
        // Simulate removing follow relationship
        UserService.mockFollowing[currentMockUserId]?.remove(uid)
        UserService.mockFollowers[uid]?.remove(currentMockUserId)
        // Similar to follow, update isFollowed state if needed.
        print("DEBUG: [Mock] Following state after unfollow: \(UserService.mockFollowing)")
        print("DEBUG: [Mock] Followers state after unfollow: \(UserService.mockFollowers)")
        return true
    }
}

// --- Remove Mock Data from Global Scope --- 
// Ensure these are removed from outside the class definition
// private func createMockUsers() -> [User] { ... }
// let mockUsers: [User] = createMockUsers()
// var mockFollowing: [String: Set<String>] = [ ... ]
// var mockFollowers: [String: Set<String>] = [ ... ]
