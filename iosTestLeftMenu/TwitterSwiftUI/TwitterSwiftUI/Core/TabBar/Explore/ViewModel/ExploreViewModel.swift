//
//  ExploreViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/25.
//

import SwiftUI
import Combine

class ExploreViewModel: ObservableObject {
    
    @Published var users = [User]()
    @Published var searchText = ""
    @Published var currentUser: User?
    @Published var showUserProfile: Bool = false
    @Published var showUserStatusDetail: Bool = false
    
    private var cancellable = Set<AnyCancellable>()
    
    var searchableUsers: [User] {
        if searchText.isEmpty {
            return self.users
        } else {
            let lowercasedQuery = searchText.lowercased()
            
            let searchedUsers = self.users.filter({
                $0.email.lowercased().contains(lowercasedQuery) ||
                $0.username.lowercased().contains(lowercasedQuery)
            })
            return searchedUsers
        }
    }
    
    init() {
        setupSubscribers()
        Task { try await self.fetchUsers() }
    }
    
    private func setupSubscribers() {
        AuthService.shared.$currentUser.sink { [weak self] user in
            self?.currentUser = user
        }
        .store(in: &cancellable)
    }
    
    @MainActor
    func fetchUsers() async throws {
        self.users = try await UserService.shared.fetchUsers()
    }
}
