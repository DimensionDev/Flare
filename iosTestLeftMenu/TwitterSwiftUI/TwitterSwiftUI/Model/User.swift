//
//  User.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

// import FirebaseFirestoreSwift
// import Firebase

struct User: Identifiable, Decodable, Hashable {
//    @DocumentID var id: String? // Remove Firebase specific property wrapper
    var id: String? // Change to simple optional String
    let username: String
    let profileImageUrl: String
    let profileHeaderImageUrl: String?
    let email: String
    let bio: String?
    let location: String?
    let webUrl: String?
    var isCurrentUserOverride: Bool? // Add override property used in mock data
    
    var isCurrentUser: Bool {
        // Prioritize the override value if it exists
        if let override = isCurrentUserOverride {
            return override
        }
        // Fallback logic (might be unreliable without AuthService access or proper context)
        // In a pure mock scenario without complex dependencies, maybe default to false?
        // Or attempt to access AuthService, risking cycles/errors.
        // For now, let's just use the override or default to false.
        return false 
//        Auth.auth().currentUser?.uid == id // Remove Firebase dependency
    }
    
    // Keep the static decode function for now, although its Firebase-specific usage is gone.
    // It might be used elsewhere or could be adapted.
    static func decode(dic: [String: Any]?) -> User {
        
        let id = dic?["uid"] as? String
        let username = dic?["username"] as? String ?? ""
        let profileImageUrl = dic?["profileImageUrl"] as? String ?? ""
        let profileHeaderImageUrl = dic?["profileHeaderImageUrl"] as? String
        let email = dic?["email"] as? String ?? ""
        let bio = dic?["bio"] as? String
        let location = dic?["location"] as? String
        let webUrl = dic?["webUrl"] as? String
        // Note: isCurrentUserOverride is not decoded here, needs manual setting if using decode
        
        return User(id: id,
                    username: username,
                    profileImageUrl: profileImageUrl,
                    profileHeaderImageUrl: profileHeaderImageUrl,
                    email: email,
                    bio: bio,
                    location: location,
                    webUrl: webUrl,
                    isCurrentUserOverride: nil // Initialize override as nil when decoding
        )
    }
}
