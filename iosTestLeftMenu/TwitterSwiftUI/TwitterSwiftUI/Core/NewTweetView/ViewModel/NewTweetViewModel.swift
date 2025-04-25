//
//  NewTweetViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/25.
//

import SwiftUI

class NewTweetViewModel: ObservableObject {

    @Published var caption = ""
    @Published var didUploadTweet = false
        
    @MainActor
    func uploadTweet() async throws {
        let success = try await TweetService.shared.uploadTweet(caption: caption)
        if success { didUploadTweet = true }
    }
}
