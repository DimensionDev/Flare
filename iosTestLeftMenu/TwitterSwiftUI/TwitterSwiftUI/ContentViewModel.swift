//
//  ContentViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import Combine
// import FirebaseAuth // Remove Firebase import

class ContentViewModel: ObservableObject {
    
    private var cancellable = Set<AnyCancellable>()
    
    @Published var userSession: User? // Change type to User?
    
    init() {
        setupSubscribers()
    }
    
    private func setupSubscribers() {
        AuthService.shared.$userSession.sink { [weak self] (user: User?) in
            self?.userSession = user
        }
        .store(in: &cancellable)
    }
}
