//
//  ChatViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/14.
//

import SwiftUI
import Firebase

class ChatViewModel: ObservableObject {
    let user: User

    @Published var messages = [Message]()
    @Published var inputMessage: String = ""

    init(user: User) {
        self.user = user
        
        Task {
            await fetchMessages()
        }
    }
    
    @MainActor
    func sendButtonTapped(with message: String, completion: @escaping () -> Void) async throws {
        do {
            try await sendMessage(message, to: user)
            completion()
        } catch {
            print("Failed sendMessage: \(error.localizedDescription)")
        }
    }
    
    @MainActor
    func fetchMessages() {
        guard let currentUser = AuthService.shared.currentUser,
                let currentUid = currentUser.id,
                let partherId = user.id else { return }

        let ref = Firestore.firestore().collection("messages")
            .document(currentUid)
            .collection(partherId)
            .order(by: "timestamp", descending: false)
        
        ref.addSnapshotListener { [weak self] snapshot, error in
            // type = .add | .modified | .removed
            guard let self = self, let changes = snapshot?.documentChanges.filter({ $0.type == .added }) else { return }
            
            changes.forEach { change in
                let messageData = change.document.data()
                guard let fromId = messageData["fromId"] as? String else { return }
                
                let messageOwner: User = fromId == currentUid ? currentUser : self.user
                // 本来であれば、データの整合性のために取得が望ましいが、チャットのやりとりは 自分と self.Userが確定なので
                // fetch(kingfisherはcacheを使うため、どちらでも良いが。。)せず、持っているUserデータを使う
//                Firestore.firestore().collection("users").document(fromId).getDocument { snapshot, _ in
//                    let user = User.decode(dic: snapshot?.data())
//                    self.messages.append(.init(user: user, dic: messageData))
//                }
                self.messages.append(.init(user: messageOwner, dic: messageData))
            }
        }
    }
    
    
    private func sendMessage(_ messageText: String, to user: User) async throws {
        guard let currentUid = AuthService.shared.userSession?.id, let userId = user.id else { return }
        let messageRef = Firestore.firestore().collection("messages")
        
        // 自分Ref
        let currentUserRef = messageRef.document(currentUid).collection(userId).document()
        let currentRecnetRef = messageRef.document(currentUid).collection("recent-messages")
        // 相手Ref
        let receivingUserRef = messageRef.document(userId).collection(currentUid)
        let receivingRecentRef = messageRef.document(userId).collection("recent-messages")
        
        let messageId = currentUserRef.documentID
        
        let data: [String: Any] = ["text": messageText,
                                   "id": messageId,
                                   "fromId": currentUid,
                                   "toId": userId, "timestamp": Timestamp(date: Date())]
        
        try await currentUserRef.setData(data)
        try await receivingUserRef.document(messageId).setData(data)
        
        // recent-messages
        try await currentRecnetRef.document(userId).setData(data)
        try await receivingRecentRef.document(currentUid).setData(data)
    }
}
 
