//
//  PreviewProvider.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import Foundation

class PreviewProvider {
    
    static let user = User(username: "username",
                    profileImageUrl: "profileImageUrl",
                    profileHeaderImageUrl: "profileHeaderImageUrl",
                    email: "email",
                    bio: "",
                    location: "",
                    webUrl: "")
    static let message = Message(user: PreviewProvider.user, dic: ["text": "テキスト"])
}
