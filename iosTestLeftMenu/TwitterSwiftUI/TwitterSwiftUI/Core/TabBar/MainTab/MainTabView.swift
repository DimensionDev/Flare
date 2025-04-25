//
//  MainTabView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI


struct MainTabView: View {
    
    private let sideBarWidth = UIScreen.main.bounds.width - 90
    
    @State private var offset: CGFloat = 0
    @State private var lastStoredOffset: CGFloat = 0
    @GestureState private var gestureOffset: CGFloat = 0
    
    @EnvironmentObject var viewModel: MainTabBarViewModel
   
    init() {
        // 为了使用自定义 TabBar，隐藏默认的 TabBar
        UITabBar.appearance().isHidden = true
    }
    
    var body: some View {
        
        HStack(spacing: 0) {
            
            SideMenuView(showSideMenu: $viewModel.showSideMenu)
            
            ZStack(alignment: .bottomTrailing) {
                
                VStack(spacing: 0) {
                    TabView(selection: $viewModel.selectedTab) {
                        
                        FeedView(showSideMenu: $viewModel.showSideMenu)
                            .tag(MainTabBarFilter.home)
                        
                        ExploreView()
                            .tag(MainTabBarFilter.explore)
                        
                        NotificationsView()
                            .tag(MainTabBarFilter.notifications)
                        
                        ConversationsView(showSideMenu: $viewModel.showSideMenu,
                                          showNewMessageView: $viewModel.showNewMessageView)
                            .tag(MainTabBarFilter.messages)

                    }
                    
                    VStack(spacing: 0) {
                        
                        Divider()
                        
                        // CustomTabBar
                        HStack(spacing: 0) {
                            ForEach(MainTabBarFilter.allCases) { tab in
                                TabButton(tab: tab)
                            }
                        }
                        .padding(.top, 15)
                        .padding(.bottom, 10)
                        .background(Color.clear.opacity(0.03)) 
                    }
                }
                .overlay(
                    Color.black  //   ( 1 or 0 ) / 5 = 1 时 opacity 为 0.2
                        .opacity( (offset / sideBarWidth) / 5.0 )
                        .ignoresSafeArea()
                        .onTapGesture {
                            viewModel.showSideMenu = false
                        }
                )
                
                NewTweetButton(selectedTab: $viewModel.selectedTab) { tab in
                    viewModel.overlayButtonTapped(on: tab)
                }
                .opacity(viewModel.hiddenNewTweetButton ? 0 : 1)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: offset == 0)
        // 因为侧边菜单和主视图会同时移动
        .frame(width: sideBarWidth + UIScreen.main.bounds.width)
        .offset(x: -sideBarWidth / 2) // 将侧边菜单隐藏到左侧
        .offset(x: offset)
        .onChange(of: viewModel.showSideMenu) { newValue in
            
            if viewModel.showSideMenu && offset == 0 {
                offset = sideBarWidth
                lastStoredOffset = sideBarWidth
            }
            
            if !viewModel.showSideMenu && offset == sideBarWidth {
                offset = 0
                lastStoredOffset = 0
            }
            
        }
        .onChange(of: gestureOffset) { _ in
            
            if gestureOffset != 0 {
                
                // 如果拖动的宽度在 SideBarWidth 范围内
                if gestureOffset + lastStoredOffset < sideBarWidth && (gestureOffset + lastStoredOffset) > 0 {
                    
                    offset = lastStoredOffset + gestureOffset
                    
                } else {
                    // 如果超出 sideMenuWidth 范围
                    if gestureOffset + lastStoredOffset < 0 {
                        // 如果拖动超过设备左边缘，则设置为 0，完全隐藏侧边菜单
                        offset = 0
                    }
                }
            }
        }
        .gesture(
            DragGesture()
                .updating($gestureOffset, body: { value, out, _ in
                    out = value.translation.width
                })
                .onEnded({ value in
                    
                    withAnimation(.spring(duration: 0.15)) {
                        
                        if value.translation.width > 0 {
                            // 拖动 >>>
                            
                            // 如果手指离开的位置超过 sideBarWidth 的一半
                            // (注意: value 通过拖动从 0 开始)
                            if value.translation.width > sideBarWidth / 2 {
                                
                                offset = sideBarWidth
                                lastStoredOffset = sideBarWidth
                                viewModel.showSideMenu = true
                            } else {
                                // 避免在侧边菜单打开状态下向右拖动时意外关闭
                                if value.translation.width > sideBarWidth && viewModel.showSideMenu {
                                    offset = 0
                                    viewModel.showSideMenu = false
                                } else {
                                    // 根据速度判断
                                    // 即使手指离开的位置不到一半，如果拖动速度快，则打开 SideMenu
                                    if value.velocity.width > 800 {
                                        offset = sideBarWidth
                                        viewModel.showSideMenu = true
                                    } else if viewModel.showSideMenu == false {
                                        // 在 showSideMenu == false 的状态下，如果手指离开的位置不到一半，则恢复原状
                                        offset = 0
                                        viewModel.showSideMenu = false
                                    }
                                }
                            }
                        } else {
                            // <<< 拖动
                            
                            if -value.translation.width > sideBarWidth / 2 {
                                offset = 0
                                viewModel.showSideMenu = false
                            } else {
                                
                                // 在侧边菜单关闭状态下向左拖动时
                                // 避免此处理
                                guard viewModel.showSideMenu else {
                                    return }
                                
                                // 即使手指离开的位置不到一半，如果向左 <<< 的拖动速度快，则关闭 sideMenu
                                if -value.velocity.width > 800 {
                                    offset = 0
                                    viewModel.showSideMenu = false
                                } else {
                                    offset = sideBarWidth
                                    viewModel.showSideMenu = true
                                }
                            }
                        }
                    }
                    
                    lastStoredOffset = offset
                })
        )
    }
    
    @ViewBuilder
    func TabButton(tab: MainTabBarFilter) -> some View {
        Button {
            withAnimation {
                viewModel.selectedTab = tab
            }
        } label: {
            Image(tab.image)
                .resizable()
                .renderingMode(.template)
                .aspectRatio(contentMode: .fit)
                .frame(width: 23, height: 23)
                .foregroundColor(viewModel.selectedTab == tab ? .primary : .gray)
                .frame(maxWidth: .infinity)
        }
    }
}

 
