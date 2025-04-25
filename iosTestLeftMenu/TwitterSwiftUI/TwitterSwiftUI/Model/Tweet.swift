//
//  Tweet.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/25.
//

// import FirebaseFirestoreSwift
// import Firebase
import Foundation // Import Foundation for Date

// Remove Decodable for now as we are creating mock data, not decoding
// struct Tweet: Identifiable, Decodable, Hashable {
struct Tweet: Identifiable, Hashable { // Keep Identifiable & Hashable
//    @DocumentID var id: String?
    var id: String? // Use simple optional String
    let caption: String
//    let timestamp: Timestamp // Change from Firebase Timestamp
    let timestamp: Date      // Change to standard Date
    let uid: String
    var likes: Int
    
    var user: User?
    //optionalの理由は firestoreからdecode時にdidLikeがないため
    var didLike: Bool? = false
    
    // Manually implement Hashable and Equatable to bypass Linter issues
    static func == (lhs: Tweet, rhs: Tweet) -> Bool {
        return lhs.id == rhs.id // Base equality on ID
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
