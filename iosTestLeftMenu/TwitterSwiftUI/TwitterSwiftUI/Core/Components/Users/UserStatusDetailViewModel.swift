//
//  UserStatusDetailViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/19.
//

import Foundation

enum FollowButtonType: Int, CaseIterable, Identifiable {
    case following
    case followers
    
    var title: String {
        switch self {
        case .following: "フォロー中"
        case .followers: "フォロワー"
        }
    }
    
    var id: Int { self.rawValue }
}

class UserStatusDetailViewModel: ObservableObject {
    
    let user: User
    
    @Published var followers = [User]()
    @Published var following = [User]()
    
    @Published var selectedTab: Int
    
    init(initialTab: FollowButtonType, user: User) {
        self.selectedTab = initialTab.rawValue
        self.user = user
        
        Task {
            try await fetchFollowers()
            try await fetchFollowing()
        }
    }
    
    @MainActor
    func fetchFollowers() async throws {
        guard let userId = user.id else { return }
        followers = try await UserService.shared.fetchFollowers(with: userId)
    }
    
    @MainActor
    func fetchFollowing() async throws {
        guard let userId = user.id else { return }
        following = try await UserService.shared.fetchFollowing(with: userId)
    }
}
