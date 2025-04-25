//
//  ProfileEditViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/20.
//

import UIKit
import Combine

class ProfileEditViewModel: ObservableObject {
    
    let user: User
    
    @Published var name: String = ""
    @Published var bio: String = ""
    @Published var location: String = ""
    @Published var webUrl: String = ""
    
    @Published var showImagePickerForProfileImage = false
    @Published var showImagePrickerForHeaderImage = false
    @Published var profileImage: UIImage? { didSet { saveButtonDisable = false }}
    @Published var headerImage: UIImage? { didSet { saveButtonDisable = false }}
    
    @Published var saveButtonDisable: Bool = true
    @Published var showProgressView: Bool = false
    
    private var cancellable = Set<AnyCancellable>()
    
    init(user: User) {
        self.user = user
        
        name = user.username
        bio = user.bio ?? ""
        location = user.location ?? ""
        webUrl = user.webUrl ?? ""
        
        setupSubscribers()
    }
    
    @MainActor
    func updateProfile() async throws {
        showProgressView = true
//        try await UserService.shared.updateProfile(profileImage: profileImage,
//                                                   headerImage: headerImage,
//                                                   name: name,
//                                                   bio: bio,
//                                                   location: location,
//                                                   webUrl: webUrl)
        showProgressView = false
    }
    
    private func setupSubscribers() {
        
        Publishers.CombineLatest4($name, $bio, $location, $webUrl)
            .map { [weak self] name, bio, location, webUrl in
                guard let self = self else { return true }
                
                return user.username == name &&
                       user.bio == bio &&
                       user.location == location &&
                       user.webUrl == webUrl &&
                       self.profileImage == nil && self.headerImage == nil
            }
            .assign(to: \.saveButtonDisable, on: self)
            .store(in: &cancellable)
    }
}
