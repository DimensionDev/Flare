//
//  UserListView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/19.
//

import SwiftUI

struct FollowingUserListView: View {
    
    @EnvironmentObject var viewModel: UserStatusDetailViewModel
    
    var tabType: FollowButtonType
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                LazyVStack {
                    ForEach(tabType == .followers ?
                            viewModel.followers : viewModel.following) { user in
                        NavigationLink {
                            ProfileView(user: user)
                        } label: {
                            UserStatusRowView(user: user)
                        }
                    }
                }
            }
            .padding(.top, 8)
        }
    }
}

#Preview {
    FollowingUserListView(tabType: .followers)
}
