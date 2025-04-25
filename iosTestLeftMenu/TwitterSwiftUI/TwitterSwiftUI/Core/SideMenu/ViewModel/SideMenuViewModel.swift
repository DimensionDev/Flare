//
//  SideMenuViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import FirebaseAuth
import Combine

enum SideMenuListType: Int, CaseIterable {
    case profile
    case lists
    case bookmarks
    case logout
    
    var title: String {
        switch self {
        case .profile: return "プロフィール"
        case .lists: return "リスト"
        case .bookmarks: return "ブックマーク"
        case .logout: return "ログアウト"
        }
    }
    
    var imageName: String {
        switch self {
        case .profile: return "person"
        case .lists: return "list.bullet"
        case .bookmarks: return "bookmark"
        case .logout: return "arrow.left.square"
        }
    }  
    
    var id: Int { self.rawValue }
}

class SideMenuViewModel: ObservableObject {

    @Published var user: User?
    @Published var followersCount: Int = 0
    @Published var follwoingCount: Int = 0
    
    private var cancellable = Set<AnyCancellable>()
    
    init() {
        setupSubscribers()
    }
    
    func singOut() {
        AuthService.shared.signOut()
    }
    
    // 画面が表示するたびにカウントを取得
    func refreshFollowStatusCount() {
        Task {
            try await self.fetchFollowingCount()
            try await self.fetchFollowersCount()
        }
    }
    
    private func setupSubscribers() {
        AuthService.shared.$currentUser.sink { [weak self] user in
            self?.user = user
        }
        .store(in: &cancellable)
        
        //Userが変更されたタイミングでFollow&Followingのカウントを取得
        $user.compactMap { $0?.id }
            .sink { [weak self] userId in
                guard let self = self else { return }
                Task {
                    try await self.fetchFollowingCount()
                    try await self.fetchFollowersCount()
                }
            }
            .store(in: &cancellable)
    }
    
    @MainActor
    private func fetchFollowingCount() async throws {
        guard let userId = user?.id else { return }
        follwoingCount = try await UserService.shared.fetchFollowingCount(with: userId)
    }
    
    @MainActor
    private func fetchFollowersCount() async throws {
        guard let userId = user?.id else { return }
        followersCount = try await UserService.shared.fetchFollowersCount(with: userId)
    }
}
