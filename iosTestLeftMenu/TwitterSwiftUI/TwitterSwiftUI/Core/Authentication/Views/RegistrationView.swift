//
//  RegistrationView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI

struct RegistrationView: View {
    
    @Environment(\.dismiss) var dismiss
    @StateObject var viewModel: RegistrationViewModel

    init() {
        _viewModel = .init(wrappedValue: RegistrationViewModel())
    }
    
    var body: some View {
        // 画面を遷移させるため使用
        NavigationStack {

            VStack {
                AuthHeaderView(title: "アカウント作成", cancelTapped: {
                    dismiss()
                })
                
                VStack(spacing: 40) {
                    CustomTextField(imageName: "envelope", placeholdeer: "メールアドレス", text: $viewModel.email)
                    CustomTextField(imageName: "person", placeholdeer: "ニックネーム", text: $viewModel.username)

                    CustomTextField(imageName: "lock",
                                    placeholdeer: "パスワード",
                                    isSecureField: true,
                                    text: $viewModel.password)
                }
                .padding(32)
                
                AuthSubmitButton(title: "次へ", width: 340) {
                    viewModel.register()
                }
                
                Spacer()
                
            }
            .edgesIgnoringSafeArea(.bottom)
            .navigationDestination(isPresented: $viewModel.didAuthenticateUser) {
                // ユーザー登録が完了したらプロフィール画像選択画面へ遷移
                ProfilePhotoSelectView()
            }
        }
    }
}

#Preview {
    RegistrationView()
}
