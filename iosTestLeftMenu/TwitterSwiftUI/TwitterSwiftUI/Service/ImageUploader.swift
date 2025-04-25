//
//  ImageUploader.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

import FirebaseStorage
import UIKit

struct ImageUploader {
    
    static func uploadProfileImage(image: UIImage, comepletion: @escaping (String) -> Void) {
         
        guard let imageData = image.aspectFittedToHeight(100).jpegData(compressionQuality: 0.25) else { return }
        
        let filename = UUID().uuidString
        let ref = Storage.storage().reference(withPath: "/profile_image/\(filename)")
        
        ref.putData(imageData) { _, error in
            if let error = error {
                print("DEBUG: Failed to profile upload image with error: \(error.localizedDescription)")
                return
            }
            
            ref.downloadURL { imageUrl, _ in
                guard let imageUrl = imageUrl?.absoluteString else { return }
                comepletion(imageUrl)
            }
        }
    }
    
    static func uploadProfileImage(image: UIImage) async throws -> String? {
         
        guard let imageData = image.aspectFittedToHeight(200).jpegData(compressionQuality: 0.25) else { return nil }
        
        let filename = UUID().uuidString
        let ref = Storage.storage().reference(withPath: "/profile_image/\(filename)")
        
        let _ = try await ref.putDataAsync(imageData)
        return try await ref.downloadURL().absoluteString
    }
    
    static func uploadProfileHeaderImage(image: UIImage) async throws -> String? {
         
        guard let imageData = image.aspectFittedToHeight(100).jpegData(compressionQuality: 0.25) else { return nil }
        
        let filename = UUID().uuidString
        let ref = Storage.storage().reference(withPath: "/profile_header_image/\(filename)")
        let _ = try await ref.putDataAsync(imageData)
        return try await ref.downloadURL().absoluteString
    }
}
