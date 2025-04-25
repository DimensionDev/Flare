//
//  ConversationsView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/14.
//

import SwiftUI

struct ConversationsView: View {
    
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel

    @Binding var showSideMenu: Bool
    @Binding var showNewMessageView: Bool
    @StateObject var viewModel: ConversationsViewModel
    @State private var navigateToChatView: Bool = false
    @State private var chatUser: User?
    
    init(showSideMenu: Binding<Bool>, showNewMessageView: Binding<Bool>) {
        _showSideMenu = showSideMenu
        _showNewMessageView = showNewMessageView
        _viewModel = .init(wrappedValue: ConversationsViewModel())
    }
    
    var body: some View {
        
        NavigationStack {
        
            ZStack {
                VStack {
                    if viewModel.recentMessages.isEmpty && !viewModel.showLoading {
                        emptyView
                            .padding(.top, 50)
                        
                        Spacer()
                        
                    } else {
                        ScrollView(.vertical, showsIndicators: false) {
                            Spacer()
                                .frame(height: 50)
                            LazyVStack {
                                ForEach(viewModel.recentMessages) { message in
                                    
                                    NavigationLink {
                                        ChatView(user: message.user)
                                            .navigationBarBackButtonHidden()
                                    } label: {
                                        ConversationRowView(message: message)
                                    }
                                }
                            }
                        }
                    }
                }
    
                if viewModel.showLoading { ProgressView() }
            }
            .onAppear {
                tabBarViewModel.updateNewTweetButton(isHidden: false)
            }
            .navigationDestination(isPresented: $navigateToChatView, destination: {
                if let user = self.chatUser {
                    ChatView(user: user)
                        .navigationBarBackButtonHidden()
                } else {
                    // TODO: ErrorViewを完成させる
                    Text("ユーザーを取得できませんでした")
                }
            })
            .sheet(isPresented: $showNewMessageView, content: {
                NewMessageView() { user in
                    self.chatUser = user
                    self.navigateToChatView = true
                }
            })
            .overlay(
                // Custom Header
                NavigationHeaderView(showSideMenu: $showSideMenu, user: $viewModel.currentUser, headerTitle: "メッセージ")
                , alignment: .top)
        }
        .background(Color.red)
    }
}

extension ConversationsView {
    
    var emptyView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("受信トレイへようこそ")
                .font(.title)
                .fontWeight(.heavy)
            
            Text("Drop a line, share posts and more with private conversations between you and others on X.")
                .foregroundStyle(.secondary)
            
            Button {
                
            } label: {
                Button {
                    tabBarViewModel.overlayButtonTapped(on: .messages)
                } label: {
                    Text("メッセージを書く")
                        .bold()
                        .foregroundStyle(.white)
                        .padding(.vertical)
                        .padding(.horizontal, 30)
                        .background(
                            RoundedRectangle(cornerRadius: 25)
                        )
                }
            }
            .padding(.top, 20)
            
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
        .padding(.vertical, 30)
        
        
    }
}

//#Preview {
//    ConversationsView(showSideMenu: .constant(false))
//}
