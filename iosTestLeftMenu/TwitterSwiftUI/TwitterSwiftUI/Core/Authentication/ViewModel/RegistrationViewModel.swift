//
//  RegistrationViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import Foundation
import Combine

class RegistrationViewModel: ObservableObject {
    
    @Published var email = ""
    @Published var username = ""
    @Published var password = ""
    
    @Published var didAuthenticateUser = false
    
    private var cancellable = Set<AnyCancellable>()
    
    init() {
        setupSubcribers()
    }

    func register() {
        AuthService.shared.register(withEmail: email,
                                    password: password,
                                    username: username)
    }
    
    private func setupSubcribers() {
        AuthService.shared.$didAuthenticateUser.sink { [weak self] didAuthenticateUser in
            self?.didAuthenticateUser = didAuthenticateUser
        }
        .store(in: &cancellable)
    }
}
