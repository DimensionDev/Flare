//
//  ProfileView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI
import Kingfisher

struct ProfileView: View {
    
    @State private var selectedTab: Int = ProfileTweetsTabType.tweets.rawValue
    @StateObject var viewModel: ProfileViewModel
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel
    // プロパティラッパー@Environmentを使用して、環境変数にアクセス
    @Environment(\.dismiss) var dismiss
    @Namespace var animation
    
    init(user: User) {
        _viewModel = .init(wrappedValue: ProfileViewModel(user: user))
    }
    
    var body: some View {
        VStack(alignment: .leading) {
            
            headerView
            
            actionButtons
            
            userInfoDetails
            
            PagerTabView(selected: $selectedTab, tabs:
                            [
                                TabLabel(type: .tweets),
                                TabLabel(type: .replies),
                                TabLabel(type: .likes)
                            ]) {
                                ForEach(ProfileTweetsTabType.allCases) { type in
                                    TweetListView(user: viewModel.user, tabType: type)
                                        .frame(width: UIScreen.main.bounds.width)
                                }
                            }
        }
        .sheet(isPresented: $viewModel.showProfileEdit, content: {
            ProfileEditView(user: viewModel.user)
                .navigationBarBackButtonHidden()
        })
        .navigationDestination(isPresented: $viewModel.showUserStatusDetail, destination: {
            UserStatusDetailView(initialTab: viewModel.selectedFollowButtonTab, user: viewModel.user)
                .navigationBarBackButtonHidden()
        })
        .onAppear {
            tabBarViewModel.showUserProfile = false
        }
        .navigationBarHidden(true)
    }
    
    @ViewBuilder
    func TabLabel(type: ProfileTweetsTabType) -> some View {
        Text(type.title)
            .font(.subheadline)
            .fontWeight(.bold)
            .padding(.horizontal, 40)
            .padding(.vertical, 6)
            .background(Color.white)
    }
    
}

#Preview {
    ProfileView(user: .init(username: "", 
                            profileImageUrl: "",
                            profileHeaderImageUrl: "profileHeaderImageUrl",
                            email: "",
                            bio: "",
                            location: "",
                            webUrl: ""))
}

extension ProfileView {
    
    var headerView: some View {
        ZStack(alignment: .bottomLeading) {
            
            if let headerImageUrl = viewModel.user.profileHeaderImageUrl {
                KFImage(URL(string: headerImageUrl))
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: .infinity)
                    .frame(height: 120)
                    .clipShape(Rectangle())
                    .edgesIgnoringSafeArea(.top)
            } else {
                Color(.systemCyan)
                    .ignoresSafeArea()
            }
        
            VStack(spacing: 0) {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "arrow.left")
                        .resizable()
                        .frame(width: 20, height: 16)
                        .foregroundColor(.white)
                }
                
                KFImage(URL(string: viewModel.user.profileImageUrl))
                    .resizable()
                    .scaledToFill()
                    .frame(width: 60, height: 60)
                    .clipShape(Circle())
                    .offset(x: 16, y: 24)
            }
        }
        .frame(height: 90)
    }
    
    var actionButtons: some View {
        HStack(spacing: 12) {
            
            Spacer()
            
            if !viewModel.user.isCurrentUser {
                Image(systemName: "bell.badge")
                    .font(.title3)
                    .padding(6)
                    .overlay(Circle().stroke(Color.gray, lineWidth: 0.75))
            }
            
            Button {
                viewModel.actionButtonTapped()
            } label: {
                Text(viewModel.actionButtonTitle)
                    .font(.subheadline).bold()
                    .frame(width: viewModel.user.isCurrentUser ? 150 : 100, height: 32)
                    .foregroundColor(viewModel.isFollowed ? .white : .black)
                    .background(
                        Color(viewModel.isFollowed ? .black : .clear)
                            .cornerRadius(20)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.gray, lineWidth: 0.75)
                    )
            }
            
            if !viewModel.user.isCurrentUser {
                NavigationLink {
                    ChatView(user: viewModel.user)
                        .navigationBarBackButtonHidden()
                } label: {
                    Text("メッセージ")
                        .font(.subheadline).bold()
                        .frame(width: 100, height: 32)
                        .foregroundColor(.black)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(Color.gray, lineWidth: 0.75)
                        )
                }
            }
        }
        .padding(.trailing)
    }
    
    var userInfoDetails: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                
                Text("\(viewModel.user.username)")
                    .font(.title2).bold()
               
                Image(systemName: "checkmark.seal.fill")
                    .foregroundColor(Color(.systemBlue))
            }
            
            Text("@\(viewModel.user.email.emailUsername ?? "")")
                .font(.subheadline)
                .foregroundColor(.gray)
           
            if let bio = viewModel.user.bio {
                Text(bio)
                    .font(.subheadline)
                    .padding(.vertical)
            }
            
            HStack(spacing: 24) {
                if let location = viewModel.user.location {
                    HStack {
                        Image(systemName: "mappin.and.ellipse")
                        Text(location)
                    }
                }
               
                if let web = viewModel.user.webUrl {
                    HStack {
                        Image(systemName: "link")
                        Text(web)
                    }
                }
            }
            .font(.caption)
            .foregroundColor(.gray)
            
            // Following / Follower
            NavigationLink {
                UserStatusDetailView(initialTab: tabBarViewModel.userStatueInitialTap, user: viewModel.user)
                    .navigationBarBackButtonHidden()
            } label: {
                UserStatsView(
                    following: $viewModel.follwoingCount,
                    followers: $viewModel.followersCount,
                    buttonTapped: { status in
                        viewModel.followButtonTapped(type: status)
                    })
                .padding(.vertical)
            }
        }
        .padding(.horizontal)
    }
    
    var tabBarButtonsView: some View {
        HStack {
            ForEach(ProfileTweetsTabType.allCases, id: \.rawValue) { item in
                VStack {
                    Text(item.title)
                        .font(.subheadline)
                        .fontWeight(selectedTab == item.rawValue ? .semibold : .regular)
                        .foregroundStyle(selectedTab == item.rawValue ? .black : .gray)
                    
                    if selectedTab == item.rawValue {
                        Capsule()
                            .foregroundStyle(Color(.systemBlue))
                            .frame(height: 3)
                            .matchedGeometryEffect(id: "FILTER", in: animation)
                    } else {
                        Capsule()
                            .foregroundStyle(Color(.clear))
                            .frame(height: 3)
                    }
                }
                .onTapGesture {
                    withAnimation(.easeInOut) {
                        selectedTab = item.rawValue
                    }
                }
            }
        }
        .overlay(
            Divider()
                .offset(x: 0, y: 16)
        )
    }
}
