//
//  UserStatusRowView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/22.
//

import SwiftUI
import Kingfisher

struct UserStatusRowView: View {
    
    @StateObject var viewModel: UserStatusRowViewModel
    
    init(user: User) {
        _viewModel = .init(wrappedValue: UserStatusRowViewModel(user: user))
    }
    
    var body: some View {
        
        HStack(alignment: .top, spacing: 12) {
            KFImage(URL(string: viewModel.user.profileImageUrl))
                .resizable()
                .scaledToFill()
                .frame(width: 48, height: 48)
                .clipShape(Circle())
            
            VStack(alignment: .leading) {
                HStack {
                    VStack(alignment: .leading) {
                        Text(viewModel.user.username)
                            .font(.subheadline).bold()
                            .foregroundStyle(.black)
                        
                        Text("@\(viewModel.user.email.emailUsername ?? "")")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    
                    FollowStatusButton(status: $viewModel.followingStatus) {
                        viewModel.followButtonTapped()
                    }
                }
                if let bio = viewModel.user.bio {
                    Text(bio)
                        .font(.subheadline)
                        .multilineTextAlignment(.leading)
                        .foregroundColor(.black)
                }
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 6)
    }
}

#Preview {
    UserStatusRowView(user: PreviewProvider.user)
}
