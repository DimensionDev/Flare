//
//  UserStatusRowViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/22.
//

import Foundation

class UserStatusRowViewModel: ObservableObject {
    let user: User
    
    @Published var followingStatus: FollowStatus = .following
    
    init(user: User) {
        self.user = user
        
        Task {
            try await fetchFollowStatus()
        }
    }
    
    func followButtonTapped() {
        Task {
           try await toggleFollowStatus()
        }
    }
    
    private func toggleFollowStatus() async throws {
        Task {
            switch followingStatus {
            case .unfollowing:
                try await follow()
            case .following:
                try await unfollow()
            }
        }
    }
    
    @MainActor
    private func unfollow() async throws {
        guard let uid = user.id else { return }
        
        followingStatus = .unfollowing
        do {
           let _ = try await UserService.shared.unfollowUser(uid: uid)
        } catch {
            print("Faild unfollow: \(error.localizedDescription)")
            followingStatus = .following
        }
    }
    
    @MainActor
    private func follow() async throws {
        guard let uid = user.id else { return }
        
        followingStatus = .following
        do {
            let _ = try await UserService.shared.followUser(uid: uid)
        } catch {
            print("Faild follow: \(error.localizedDescription)")
            followingStatus = .unfollowing
        }
    }
    
    @MainActor
    private func fetchFollowStatus() async throws {
        guard let uid = user.id else { return }
        let followed = try await UserService.shared.checkIfUserIsFollowing(for: uid)
        followingStatus = followed ? .following : .unfollowing
    }
}
