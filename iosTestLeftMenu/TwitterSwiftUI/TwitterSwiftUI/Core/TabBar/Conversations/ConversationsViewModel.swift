//
//  ConversationsViewModel.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/14.
//

import Firebase
import Combine

class ConversationsViewModel: ObservableObject {
    
    @Published var recentMessages: [Message] = []
    @Published var currentUser: User?
    @Published var showLoading: Bool = false
    
    var cancellable = Set<AnyCancellable>()
    
    // Recentメッセージはユーザーに対して1つのメッセージのみを保証
    private var recentMessagesDic = [String: Message]()
    
    init() {
        setupSubscribers()
        fetchRecentMessages()
    }
    
    func setupSubscribers() {
     
        AuthService.shared.$currentUser.sink { [weak self] user in
            self?.currentUser = user
        }
        .store(in: &cancellable)
    }
    
    func fetchRecentMessages() {
        guard let currentUid = AuthService.shared.userSession?.id else { return }
        
        let recentMessagesRef = Firestore.firestore().collection("messages").document(currentUid).collection("recent-messages")
        recentMessagesRef.order(by: "timestamp", descending: true)
        
        showLoading = true
        recentMessagesRef.addSnapshotListener { [weak self] snapshot, error in
            guard let changes = snapshot?.documentChanges, let self = self else { return }
            
            if changes.isEmpty { self.showLoading = false }
            
            changes.forEach { change in
                let messageData = change.document.data()
                let partnerUId = change.document.documentID
                
                // 配列に既に相手が存在する場合
                if self.recentMessagesDic.keys.contains(partnerUId), let user = self.recentMessagesDic[partnerUId]?.user {
    
                    self.recentMessagesDic[partnerUId] = .init(user: user, dic: messageData)
                    
                    //最新データがトップにくるように並べ替え
                    self.sortMessagesByLatest()
                    self.showLoading = false
                } else {
                    
                    Firestore.firestore().collection("users").document(partnerUId).getDocument { [weak self] snapshot, _ in
                        guard let self = self else { return }
                        let user = User.decode(dic: snapshot?.data())
                        
                        self.recentMessagesDic[partnerUId] = .init(user: user, dic: messageData)
                       
                        //最新データがトップにくるように並べ替え
                        sortMessagesByLatest()
                        self.showLoading = false
                    }
                }
            }
        }
        
        // --- Add Mock Message Data --- 
        // Create mock messages between current user (mock_user_1) and others
        // Use UserService.mockUsers to access static mock data
        let mockPartners = UserService.mockUsers.filter { $0.id != currentUid }.prefix(3) // Take first 3 other users
        var tempMessages: [Message] = []
        let now = Date()
    }
    
    private func sortMessagesByLatest() {
        let messages = Array(self.recentMessagesDic.values)
        let sorted = messages.sorted(by: { $0.timestamp.dateValue() > $1.timestamp.dateValue() })
        self.recentMessages = sorted
    }
}
