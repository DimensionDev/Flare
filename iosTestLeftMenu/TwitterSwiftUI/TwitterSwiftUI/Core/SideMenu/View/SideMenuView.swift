//
//  SideMenuView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI
import Kingfisher

struct SideMenuView: View {
    
    @StateObject var viewModel: SideMenuViewModel
    @Binding var showSideMenu: Bool
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel
    
    init(showSideMenu: Binding<Bool>) {
        _showSideMenu = showSideMenu
        _viewModel = .init(wrappedValue: SideMenuViewModel())
    }
    
    var body: some View {
        
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading) {
                
                Group {
                    if let url = viewModel.user?.profileImageUrl {
                        KFImage(URL(string: url))
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 48, height: 48)
                            .clipShape(Circle())
                        
                    } else {
                        Image(systemName: "person.fill")
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 48, height: 48)
                            .clipShape(Circle())
                    }
                    
                    Text("\(viewModel.user?.username ?? "")")
                        .font(.headline).bold()
                    
                    Text("@\(viewModel.user?.email.emailUsername ?? "")")
                        .font(.caption)
                        .foregroundStyle(.gray)
                }
                .onTapGesture {
                    showSideMenu = false
                    tabBarViewModel.showUserProfile = true
                }
                
                UserStatsView(
                    following: $viewModel.follwoingCount,
                    followers: $viewModel.followersCount,
                    buttonTapped: { status in
                        showSideMenu = false
                        tabBarViewModel.userStatueInitialTap = status
                        tabBarViewModel.showUserStatusDetail.toggle()
                    })
                .padding(.vertical)
            }
            
            ForEach(SideMenuListType.allCases, id: \.self) { type in
                switch type {
                case .profile:
                    SideMenuOptionRowView(type: type)
                        .onTapGesture {
                            showSideMenu = false
                            tabBarViewModel.showUserProfile = true
                        }
                case .lists: SideMenuOptionRowView(type: type)
                case .bookmarks: SideMenuOptionRowView(type: type)
                case .logout:
                    Button {
                        viewModel.singOut()
                    } label: {
                        SideMenuOptionRowView(type: type)
                    }
                }
            }
            
            Spacer()
            
        }
        .onChange(of: showSideMenu, perform: { _ in
            viewModel.refreshFollowStatusCount()
        })
        .padding(.leading, 20)
        .frame(width: UIScreen.main.bounds.width - 90, alignment: .leading)
        .background(Color.white)
    }
}
 
