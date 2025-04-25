//
//  ProfileViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/25.
//

import Foundation
import Combine

class ProfileViewModel: ObservableObject {
    
    @Published var user: User

    @Published var isFollowed = false
    @Published var followersCount: Int = 0
    @Published var follwoingCount: Int = 0
    @Published var showProfileEdit: Bool = false
    @Published var showUserStatusDetail: Bool = false
    
    var selectedFollowButtonTab: FollowButtonType = .followers
    
    var actionButtonTitle: String {
        user.isCurrentUser ? "プロフィールを編集" : isFollowed ? "フォロー中" : "フォローする"
    }
    
    private var cancellable = Set<AnyCancellable>()
    
    init(user: User) {
        self.user = user
        
        Task {
            try await checkIfUserIsFollowing()
            try await fetchFollowingCount()
            try await fetchFollowersCount()
        }
        
        if AuthService.shared.userSession?.id == user.id {
            // プロフィール編集で更新があった場合、更新をするため
            setupSubscribers()
        }
    }
    
    // MARK: From View
    func actionButtonTapped() {
        
        if user.isCurrentUser {
            showProfileEdit.toggle()
            
        } else {
            // Follow or Following
            Task {
                if isFollowed {
                    try await unfollow()
                } else {
                    try await follow()
                }
            }
        }
    }
    
    func followButtonTapped(type: FollowButtonType) {
        selectedFollowButtonTab = type
        showUserStatusDetail.toggle()
    }
    
    // MARK: Private
    
    private func setupSubscribers() {
        AuthService.shared.$currentUser.sink { [weak self] user in
            guard let self = self, let unwapped = user else { return }
            self.user = unwapped
        }
        .store(in: &cancellable)
    }

    @MainActor
    private func checkIfUserIsFollowing() async throws {
        guard let uid = user.id else { return }
        self.isFollowed = try await UserService.shared.checkIfUserIsFollowing(for: uid)
    }
    
    // MARK: Privates
    
    @MainActor
    private func follow() async throws {
        guard let uid = user.id else { return }
        self.isFollowed = try await UserService.shared.followUser(uid: uid)
    }
    
    @MainActor
    func unfollow() async throws {
        guard let uid = user.id else { return }
        let unfollowed = try await UserService.shared.unfollowUser(uid: uid)
        self.isFollowed = !unfollowed
    }
    
    @MainActor
    private func fetchFollowingCount() async throws {
        guard let userId = user.id else { return }
        follwoingCount = try await UserService.shared.fetchFollowingCount(with: userId)
    }
    
    @MainActor
    private func fetchFollowersCount() async throws {
        guard let userId = user.id else { return }
        followersCount = try await UserService.shared.fetchFollowersCount(with: userId)
    }
}
