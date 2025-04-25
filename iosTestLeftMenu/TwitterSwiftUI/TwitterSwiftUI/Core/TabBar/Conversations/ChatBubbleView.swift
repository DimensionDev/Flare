//
//  ChatBubbleView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/18.
//

import SwiftUI
import Kingfisher

struct ChatBubbleView: View {
    
    var message: Message
    
    var body: some View {
        HStack {
            if message.isFromCurrentUser {
                Spacer(minLength: 60)
                messageBubble
            } else {
                partnerMessageBubble
                Spacer(minLength: 60)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal)
        .padding(.vertical, 3)
    }
}

extension ChatBubbleView {
    
    var partnerMessageBubble: some View {
        HStack(alignment: .top) {
            KFImage(URL(string: message.user.profileImageUrl))
                .resizable()
                .scaledToFill()
                .frame(width: 30, height: 30)
                .clipShape(Circle())
            
            HStack(alignment: .bottom, spacing: 2) {
                Text(message.text)
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    .foregroundStyle(.black)
                    .background(.gray.opacity(0.15))
                    .clipShape(RoundedShape(corners: [.bottomLeft, .bottomRight, .topRight], cornerRadius: 18))
                Text(message.timestamp.timeStringJP())
                    .font(.system(size: 9))
                    .foregroundStyle(.gray)
            }
        }
    }
    
    var messageBubble: some View {
        HStack(alignment: .bottom, spacing: 2) {
            Text(message.timestamp.timeStringJP())
                .font(.system(size: 9))
                .foregroundStyle(.gray)
            
            Text(message.text)
                .padding(.horizontal)
                .padding(.vertical, 8)
                .foregroundStyle(.white)
                .background(Color(.systemBlue))
                .clipShape(RoundedShape(corners: [.topLeft, .bottomLeft, .bottomRight], cornerRadius: 18))
        }
    }
}

#Preview {
    ChatBubbleView(message: PreviewProvider.message)
}
