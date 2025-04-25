//
//  ConversationRowView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/14.
//

import SwiftUI
import Kingfisher

struct ConversationRowView: View {
    
    let message: Message
    
    var body: some View {
        
        HStack(spacing: 12) {
            KFImage(URL(string: message.user.profileImageUrl))
                .resizable()
                .scaledToFill()
                .frame(width: 48, height: 48)
                .clipShape(Circle())
            
            VStack(alignment: .leading) {
                HStack {
                    Text(message.user.username)
                        .font(.subheadline).bold()
                        .foregroundStyle(.black)
                    
                    Text("@\(message.user.email.emailUsername ?? "")")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                    
                    Text("\(message.timestamp.timeStringJP())")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
                Text(message.text)
                    .font(.subheadline).bold()
                    .foregroundStyle(.black)
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)
            }
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 4)
    }
}

#Preview {
    ConversationRowView(message: PreviewProvider.message)
}
