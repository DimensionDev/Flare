//
//  EntryView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import SwiftUI

struct EntryView: View {
    var body: some View {
        
        NavigationStack {
            VStack(spacing: 14) {
                xLogo
                    .padding(.top, 10)
                
                Spacer()
                Text("「いま」起きていること\nをみつけよう。")
                    .font(.title)
                    .fontWeight(.black)
                
                Spacer()
                
                Group {
                    AuthButton(with: .signInWithGoole)
                    AuthButton(with: .signInWithApple)
                    
                    HStack {
                        Capsule()
                            .fill(.gray)
                            .frame(height: 0.8)
                        
                        Text("または")
                            .font(.caption)
                            .foregroundStyle(.gray)
                        
                        Capsule()
                            .fill(.gray)
                            .frame(height: 0.8)

                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 15)
                    
                    NavigationLink {
                        RegistrationView()
                            .navigationBarBackButtonHidden()
                    } label: {
                        AuthButton(with: .createAccount)
                    }
                    
                    HStack {
                        Text("登録すると") +
                        Text("利用規約").foregroundColor(Color(.systemBlue)) +
                        Text("、") +
                        Text("プライバシーポリシー").foregroundColor(Color(.systemBlue)) +
                        Text("、") +
                        Text("Cookieの使用").foregroundColor(Color(.systemBlue)) +
                        Text("に同意したものとみなされます。")
                    }
                    .font(.caption)
                    .fontWeight(.regular)
                    .lineSpacing(6)
                    .padding(.top, 10)
                    
                    HStack(spacing: 0) {
                        Text("アカウントをお持ちの方は")
                            .foregroundStyle(Color.black.opacity(0.8))
                            .font(.footnote)
                        
                        NavigationLink {
                            LoginView()
                                .navigationBarBackButtonHidden()
                        } label: {
                            Text("ログイン")
                                .font(.footnote)
                        }
                    }
                    .padding(.vertical, 40)
                    
                }
                .padding(.horizontal, 30)
                
            }
            .padding(.bottom, 10)
            .edgesIgnoringSafeArea(.bottom)
        }
        
    }
    
    @ViewBuilder
    func AuthButton(with type: AuthButtonType) -> some View {
        HStack(spacing: 12) {
            if let imagename = type.image {
                Image(imagename)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 25, height: 25)
            }
    
            Text(type.title)
                .font(.subheadline).bold()
                .foregroundStyle(type == .createAccount ? .white : .black)
        }
        .frame(height: 50)
        .frame(maxWidth: .infinity)
        .background(type.backgroundColor)
        .cornerRadius(25)
        .overlay(
            RoundedRectangle(cornerRadius: 25)
                .strokeBorder(.gray.opacity(0.7), lineWidth: 1)
        )
    }
}

#Preview {
    EntryView()
}
