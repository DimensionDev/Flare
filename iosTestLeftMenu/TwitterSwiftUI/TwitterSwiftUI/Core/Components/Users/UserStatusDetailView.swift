//
//  UserStatusDetailView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/17.
//

import SwiftUI

struct UserStatusDetailView: View {
    
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel
    @StateObject var viewModel: UserStatusDetailViewModel
    
    init(initialTab: FollowButtonType, user: User) {
        _viewModel = .init(wrappedValue: UserStatusDetailViewModel(initialTab: initialTab, user: user))
    }
    var body: some View {
        VStack(spacing: 0) {
            PagerTabView(selected: $viewModel.selectedTab, tabs:
                            [
                                TabLabel(type: .following),
                                TabLabel(type: .followers)
                            ]) {
                                ForEach(FollowButtonType.allCases) { type in
                                    FollowingUserListView(tabType: type)
                                        .environmentObject(self.viewModel)
                                        .frame(width: UIScreen.main.bounds.width)
                                }
                            }
        }
        .padding(.top, 30)
        .overlay(navigationHeader.edgesIgnoringSafeArea(.top), alignment: .top)
    }
    
    @ViewBuilder
    func TabLabel(type: FollowButtonType) -> some View {
        Text(type.title)
            .font(.subheadline)
            .fontWeight(.bold)
            .padding(.horizontal, 40)
            .padding(.top, 3)
            .padding(.bottom, 6)
            .background(Color.white)
    }
}

extension UserStatusDetailView {
    var navigationHeader: some View {
        HStack {
            Button {
                dismiss()
            } label: {
                HStack {
                    Image(systemName:"chevron.left")
                    Text(viewModel.user.username)
                        .bold()
                }
            }
            .font(.subheadline)
            .foregroundColor(.black)
            
            Spacer()
        }
        .padding(.top, 60)
        .padding([.leading, .bottom])
        .background(BlurView().ignoresSafeArea())
    }
}

#Preview {
    UserStatusDetailView(initialTab: .followers,
                         user: .init(username: "username",
                                     profileImageUrl: "profileImageUrl",
                                     profileHeaderImageUrl: "profileHeaderImageUrl",
                                     email: "email",
                                     bio: "",
                                     location: "",
                                     webUrl: ""))
}
