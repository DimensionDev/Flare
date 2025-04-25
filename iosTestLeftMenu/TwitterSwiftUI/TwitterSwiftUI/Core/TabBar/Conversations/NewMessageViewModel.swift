//
//  NewMessageViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/15.
//

import Foundation
import Combine

class NewMessageViewModel: ObservableObject {
    
    @Published var searchedText: String = ""
    @Published var users = [User]()
    private var cancellable = Set<AnyCancellable>()
    
    init() {
        setupSubscribers()
    }
    
    private func setupSubscribers() {
        
        $searchedText
            .removeDuplicates()
            .debounce(for: 0.3, scheduler: RunLoop.main)
            .sink { [weak self] query in
                guard let self = self else { return }
                Task {
                    try await self.fetchUsers(with: query)
                }
            }
            .store(in: &cancellable)
    }
    
    @MainActor
    func fetchUsers(with query: String) async throws {
        let users = try await UserService.shared.fetchUsers(with: query)
        self.users = users
    }
}
