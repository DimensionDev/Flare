//
//  UserRowView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI
import Kingfisher

struct UserRowView: View {
    
    let user: User
    
    var body: some View {
        
        HStack(spacing: 12) {
            KFImage(URL(string: user.profileImageUrl))
                .resizable()
                .scaledToFill()
                .frame(width: 48, height: 48)
                .clipShape(Circle())
            
            VStack(alignment: .leading) {
                Text(user.username)
                    .font(.subheadline).bold()
                    .foregroundStyle(.black)
                
                Text("@\(user.email.emailUsername ?? "")")
                    .font(.subheadline)
                    .foregroundColor(.gray)
            }
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 4)
    }
}

#Preview {
    UserRowView(user: .init(username: "",
                            profileImageUrl: "",
                            profileHeaderImageUrl: "profileHeaderImageUrl",
                            email: "",
                            bio: "",
                            location: "",
                            webUrl: ""))
}
