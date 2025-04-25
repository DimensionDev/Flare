//
//  ChatView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI

struct ChatView: View {
    
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var tabBarViewModel: MainTabBarViewModel
    @StateObject var viewModel: ChatViewModel
    
    @State var scrollViewProxy: ScrollViewProxy?
    
    init(user: User) {
        _viewModel = .init(wrappedValue: ChatViewModel(user: user))
    }
    
    var body: some View {
        
        VStack(spacing: 0) {
            
            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    Spacer()
                        .frame(height: 40)
                    
                    LazyVStack {
                        ForEach(viewModel.messages) { message in
                            ChatBubbleView(message: message)
                               // .id(message.id)
                        }
                        
                        // これもscrollAtの一つの方法
                        Spacer()
                            .frame(height: 10)
                            .id("SCROLL")
                    }
                }
                .onChange(of: viewModel.messages.count) { _ in
                    //メッセージリストに変更がある場合、最後のメッセージまでスクロール
                   // guard let messageId = viewModel.messages.last?.id else { return }
                    withAnimation(.spring(duration: 0.1)) {
                        proxy.scrollTo("SCROLL", anchor: .bottom)
                    }
                }
            }
            
            messageInputView
        }
        .overlay(navigationHeader.edgesIgnoringSafeArea(.top), alignment: .top)
        .onAppear {
            tabBarViewModel.updateNewTweetButton(isHidden: true)
        }
    }
}

extension ChatView {
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
    
    var messageInputView: some View {
        HStack(alignment: .bottom) {
            TextField("メッセージを書く", text: $viewModel.inputMessage, axis: .vertical)
                .textFieldStyle(PlainTextFieldStyle())
                .frame(minHeight: 30)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.gray.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(Color.gray.opacity(0.1), lineWidth: 1)
                )
            Button {
                Task {
                    try await viewModel.sendButtonTapped(with: viewModel.inputMessage) {
                        viewModel.inputMessage = ""
                    }
                }
            } label: {
                Image(systemName: "paperplane.fill")
                    .bold()
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                    )
                    .padding(.bottom, 3)
            }
            .disabled(viewModel.inputMessage.isEmpty)
        }
        .background()
        .padding(.horizontal)
        .padding(.vertical, 8)
        .overlay(Divider().opacity(0.5), alignment: .top)
    }
}

#Preview {
    ChatView(user: PreviewProvider.user)
}
