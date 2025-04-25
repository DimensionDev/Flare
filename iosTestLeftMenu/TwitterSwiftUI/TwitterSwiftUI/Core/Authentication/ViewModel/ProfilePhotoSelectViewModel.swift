//
//  ProfilePhotoSelectViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import UIKit

class ProfilePhotoSelectViewModel: ObservableObject {
    @Published var showImagePicker = false
    @Published var image: UIImage?
    
    func uploadProfileImage(_ image: UIImage) {
//        AuthService.shared.uploadProfileImage(image)
    }
}
