//
//  ExploreView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI
import Kingfisher

struct ExploreView: View {
    
    @StateObject var viewModel = ExploreViewModel()
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel
    
    var body: some View {
        
        NavigationStack {
            VStack(spacing: 10) {
                headerView
                
                ScrollView {
                    LazyVStack {
                        ForEach(viewModel.searchableUsers) { user in
                            NavigationLink {
                                ProfileView(user: user)
                            } label: {
                                UserRowView(user: user)
                            }
                        }
                    }
                }
            }
            .onChange(of: tabBarViewModel.showUserStatusDetail) { _ in
                if tabBarViewModel.selectedTab == .explore {
                    viewModel.showUserStatusDetail.toggle()
                }
            }
            .navigationDestination(isPresented: $viewModel.showUserStatusDetail, destination: {
                if let user = viewModel.currentUser {
                    UserStatusDetailView(initialTab: tabBarViewModel.userStatueInitialTap, user: user)
                        .navigationBarBackButtonHidden()
                }
            })
            .onChange(of: tabBarViewModel.showUserProfile) { _ in
                if tabBarViewModel.selectedTab == .explore {
                    viewModel.showUserProfile = true
                }
            }
            .navigationDestination(isPresented: $viewModel.showUserProfile, destination: {
                if let user = viewModel.currentUser {
                    ProfileView(user: user)
                }
            })
            .fullScreenCover(isPresented: $tabBarViewModel.showNewTweetView, content: {
                // 新しいTweet投稿画面をmodalで表示
                NewTweetView()
            })
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

extension ExploreView {
    
    var headerView: some View {
        HStack {
            Button {
                tabBarViewModel.showSideMenu = true
            } label: {
                Group {
                    if let iconUrl = viewModel.currentUser?.profileImageUrl {
                        KFImage(URL(string: iconUrl))
                            .resizable()
                            .scaledToFill()
                    } else {
                        Image(systemName: "person.fill")
                            .resizable()
                            .scaledToFill()
                    }
                }
                .foregroundColor(.gray)
                .background(.gray.opacity(0.3))
                .frame(width: 35, height: 35)
                .clipShape(Circle())
            }
            
            SearchBar(text: $viewModel.searchText)
        }
        .padding(.horizontal)
    }
}

#Preview {
    ExploreView()
}
