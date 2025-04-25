//
//  NewTweetView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI
import Kingfisher

struct NewTweetView: View {
    
    @Environment(\.dismiss) var dismiss
    @StateObject var viewModel: NewTweetViewModel = NewTweetViewModel()

    var body: some View {
        VStack {
            HStack {
                Button {
                    // TODO: caption.isEmpTy == falseの場合にはアラートを出す
                    dismiss()
                } label: {
                    Text("キャンセル")
                        .foregroundStyle(.black)
                }
                
                Spacer()
                
                Button {
                    Task { try await viewModel.uploadTweet() }
                } label: {
                    Text("ポストする")
                        .font(.caption)
                        .bold()
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(viewModel.caption.isEmpty ? .gray.opacity(0.7) : Color(.systemBlue))
                        .clipShape(Capsule())
                }
                .disabled(viewModel.caption.isEmpty)


            }
            .padding()
            
            HStack(alignment: .top) {
                if let user = AuthService.shared.currentUser {
                    KFImage(URL(string: user.profileImageUrl))
                        .resizable()
                        .scaledToFill()
                        .frame(width: 30, height: 30)
                        .clipShape(Circle())
                }
                
                TextAreaView("いまどうしている？", text: $viewModel.caption)
                    .offset(y: -8)
            }
            .padding()
        }
        .onReceive(viewModel.$didUploadTweet, perform: { success in
            if success {
                dismiss()
            } else {
                // show error
            }
        })
    }
}

#Preview {
    NewTweetView()
}
