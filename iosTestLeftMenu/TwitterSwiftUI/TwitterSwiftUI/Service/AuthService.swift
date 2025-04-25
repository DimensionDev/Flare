//
//  AuthService.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/8.
//

import SwiftUI
// import Firebase
// import FirebaseAuth
// import FirebaseFirestore

class AuthService: ObservableObject {
    
    static let shared = AuthService()
    
    @Published var userSession: User?
    @Published var didAuthenticateUser: Bool = false
    
    // This might not be needed anymore if registration flow is simplified
    private var tempUserSession: User?
    
    // User
    @Published var currentUser: User?
    
    private init() {
        // Initialize with mock current user directly for simplicity
        print("DEBUG: [Mock] AuthService init. Setting mock current user.")
        Task {
             // Use the accessible ID from UserService
            self.currentUser = try? await UserService.shared.fetchUser(withUid: UserService.shared.currentMockUserId)
            self.userSession = self.currentUser // Set userSession as well
            print("DEBUG: [Mock] AuthService init completed. Current user: \(self.currentUser?.username ?? "nil")")
        }
//        self.userSession = Auth.auth().currentUser // Removed Firebase call
//        Task { try await self.fetchUserProfile() } // Fetching handled differently now
    }
    
    // Mock Login - Simply sets the predefined mock user as current
    func login(withEmail email: String, password: String) async throws  {
        print("DEBUG: [Mock] login called with Email: \(email)")
        // In a real mock, you might check against mock credentials
        // For now, assume login is always successful with the main mock user
        self.currentUser = try? await UserService.shared.fetchUser(withUid: UserService.shared.currentMockUserId)
        self.userSession = self.currentUser
        print("DEBUG: [Mock] Login successful. Current user set to: \(self.currentUser?.username ?? "nil")")
    }
    
    // Mock Register - Simplified, just sets auth flag
    func register(withEmail email: String, password: String, username: String) {
         print("DEBUG: [Mock] register called with Email: \(email), Username: \(username)")
        // Remove Firebase createUser call and Firestore data saving
        // Just simulate the completion that would trigger navigation/UI change
        self.didAuthenticateUser = true
        print("DEBUG: [Mock] Registration simulated. didAuthenticateUser set to true.")
        
//        Auth.auth().createUser(withEmail: email, password: password) { result, error in ... } // Removed
    }
    
    // Mock SignOut
    func signOut() {
        print("DEBUG: [Mock] signOut called.")
        userSession = nil
        currentUser = nil
//        try? Auth.auth().signOut() // Removed Firebase call
    }
    
    // Mock Upload Profile Image - Simplified, does nothing persistent
    // The original logic relied on tempUserSession after registration, which is now simplified.
    func uploadProfileImage() {
         print("DEBUG: [Mock] uploadProfileImage called. Doing nothing.")
        // Remove ImageUploader call and Firestore update
        
//        guard let uid = tempUserSession?.uid else { return } // tempUserSession usage removed
//        ImageUploader.uploadProfileImage(...) { ... } // Removed
        
        // If a post-registration flow needs simulation, add it here.
        // For example, maybe immediately set userSession after mock registration?
        // Depends on the desired test flow.
        // self.userSession = self.tempUserSession // Original logic position
        
        // Since register is simplified, let's assume user is already "logged in"
        // So maybe just call refresh?
        Task { try? await refreshCurrentUser() }

    }
    
    // Mock Refresh Current User
    func refreshCurrentUser() async throws {
        print("DEBUG: [Mock] refreshCurrentUser called.")
        try await fetchUserProfile()
    }
    
    // Mock Fetch User Profile
    @MainActor
    private func fetchUserProfile() async throws {
         print("DEBUG: [Mock] fetchUserProfile called.")
        // Fetch the current mock user using the ID from UserService
        self.currentUser = try await UserService.shared.fetchUser(withUid: UserService.shared.currentMockUserId)
        // Optionally update userSession if it should always mirror currentUser
        // self.userSession = self.currentUser
        print("DEBUG: [Mock] fetchUserProfile completed. Current user: \(self.currentUser?.username ?? "nil")")
//        guard let uid = userSession?.uid else { return } // Original check based on FirebaseAuth.User ID
//        self.currentUser = try await UserService.shared.fetchUser(withUid: uid) // Original fetch
    }
}

