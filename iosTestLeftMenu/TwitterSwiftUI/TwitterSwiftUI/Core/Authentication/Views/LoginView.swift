//
//  LoginView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI

struct LoginView: View {
    
    @StateObject var viewModel: LoginViewModel
  
    @Environment(\.dismiss) var dismiss

    init() {
        _viewModel = .init(wrappedValue: LoginViewModel())
    }

    var body: some View {
        
        VStack(spacing: 0) {
           
            AuthHeaderView(title: "始めるには、メールアドレスとパスワードを入力してください", cancelTapped: {
                dismiss()
            })
            
            VStack(spacing: 40) {
                CustomTextField(imageName: "envelope", placeholdeer: "メールアドレス", text: $viewModel.email)
                CustomTextField(imageName: "lock",
                                placeholdeer: "パスワード",
                                isSecureField: true,
                                text: $viewModel.password)
            }
            .padding(.horizontal, 25)

            AuthSubmitButton(title: "ログイン", width: 340) {
                Task { try await viewModel.login() }
            }
            .padding(.top, 20)
            
            Spacer()
            
            HStack {
                Text("パスワードを忘れた場合はこちら")
                    .font(.callout)
                    .foregroundStyle(.black.opacity(0.7))
                
                Button {
                    
                } label: {
                    Text("次へ")
                        .font(.callout)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.vertical, 8)
                        .padding(.horizontal)
                        .background(.gray)
                        .cornerRadius(20)
                }
            }
            .padding(.bottom, 40)
        }
        .edgesIgnoringSafeArea(.bottom)
    }
}

#Preview {
    LoginView()
}
 
