//
//  Message.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/14.
//

import Firebase

struct Message: Identifiable {
    let text: String
    let user: User
    let toId: String
    let fromId: String
    let isFromCurrentUser: Bool
    let timestamp: Timestamp
    let id: String
    
    var chatPartnerId: String { isFromCurrentUser ? toId : fromId }
    
    init(user: User, dic: [String: Any]) {
        self.user = user
        
        self.text = dic["text"] as? String ?? ""
        self.toId = dic["toId"] as? String ?? ""
        self.fromId = dic["fromId"] as? String ?? ""
        self.isFromCurrentUser = fromId == Auth.auth().currentUser?.uid
        self.timestamp = dic["timestamp"] as? Timestamp ?? Timestamp(date: Date())
        self.id = dic["id"] as? String ?? ""
    }
}
