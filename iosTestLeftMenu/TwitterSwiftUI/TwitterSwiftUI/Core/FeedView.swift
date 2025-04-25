//
//  FeedView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI
import Kingfisher

struct FeedView: View {
    
    @Binding var showSideMenu: Bool
    
    @Namespace var animation
    @StateObject var viewModel = FeedViewModel()
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel

    var body: some View {
        
        NavigationStack {
            
            VStack(spacing: 0) {
             
                NavigationHeaderView(showSideMenu: $showSideMenu, user: $viewModel.currentUser)
                
                ZStack(alignment: .leading) {
                   
                    PagerTabView(
                        selected: $viewModel.currentTab, tabs:
                            [
                                TabLabel(type: .recommend),
                                TabLabel(type: .following),
                            ]
                    ) {
                        ForEach(FeedTabFilter.allCases) { type in
                            FeedTabListView(filterType: type)
                                .frame(width: UIScreen.main.bounds.width)
                        }
                    }
                    
                    // 左エッジからDragしてSideMenuのoffsetがtriggerするように
                    // 左に透明のViewを設ける
                    Color.white.opacity(0.0001)
                        .frame(width: 30)
                }
            }
            .onChange(of: tabBarViewModel.showUserStatusDetail) { _ in
                if tabBarViewModel.selectedTab == .home {
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
                if tabBarViewModel.selectedTab == .home {
                    viewModel.showUserProfile = true
                }
            }
            .navigationDestination(isPresented: $viewModel.showUserProfile, destination: {
                if let user = viewModel.currentUser {
                    ProfileView(user: user)
                }
            })
        }
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(isPresented: $tabBarViewModel.showNewTweetView, content: {
            // 新しいTweet投稿画面をmodalで表示
            NewTweetView()
        })
    }
    
    @ViewBuilder
    func TabLabel(type: FeedTabFilter) -> some View {
        Text(type.title)
            .font(.subheadline)
            .fontWeight(.bold)
            .padding(.horizontal, 40)
            .padding(.top, 3)
            .padding(.bottom, 6)
            .background(Color.white)
    }
    
}

#Preview {
    FeedView(showSideMenu: .constant(false))
}
