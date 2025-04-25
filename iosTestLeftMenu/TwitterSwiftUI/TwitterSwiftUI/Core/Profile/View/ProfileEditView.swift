//
//  ProfileEditView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/20.
//

import SwiftUI
import Kingfisher

struct ProfileEditView: View {
    
    @StateObject var viewModel: ProfileEditViewModel
    @Environment(\.dismiss) var dismiss
    
    init(user: User) {
        _viewModel = .init(wrappedValue: ProfileEditViewModel(user: user))
    }
    
    var body: some View {
        
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                
                headerImage
                    .padding(.top, 40)
                
                profileImage
                
                nameInput
                
                bioInput
                
                loactionInput
                
                webUrlInput
                
                Spacer()
            }
            .overlay(header, alignment: .top)
            .sheet(isPresented: $viewModel.showImagePickerForProfileImage , content: {
                ImagePicker(selectedImage: $viewModel.profileImage)
            })
            .sheet(isPresented: $viewModel.showImagePrickerForHeaderImage, content: {
                ImagePicker(selectedImage: $viewModel.headerImage)
            })
            
            if viewModel.showProgressView {
                Color.black.opacity(0.1).ignoresSafeArea()
                ProgressView()
            }
        }
    }
}

extension ProfileEditView {
    var header: some View {
        ZStack {
            HStack {
                Button {
                    dismiss()
                } label: {
                    Text("キャンセル")
                }
                Spacer()
                
                Button {
                    Task {
                        try await viewModel.updateProfile()
                        dismiss()
                    }
                } label: {
                    Text("保存")
                        .opacity(viewModel.saveButtonDisable ? 0.5 : 1)
                }
                .disabled(viewModel.saveButtonDisable)
            }
            
            Text("編集")
                .font(.headline)
                .bold()
        }
        .foregroundStyle(.black)
        .padding(.horizontal)
        .padding(.vertical, 6)
        .padding(.top, 5)
    }
    
    var headerImage: some View {
        ZStack {
            
            Group {
                if let selectedImageFromImagePicker = viewModel.headerImage {
                    Image(uiImage: selectedImageFromImagePicker)
                        .resizable()
                        .scaledToFill()
                } else if let headerImageUrl = viewModel.user.profileHeaderImageUrl {
                    KFImage(URL(string: headerImageUrl))
                        .resizable()
                        .scaledToFill()
                } else {
                    Color.gray
                        .opacity(0.6)
                }
            }
            .frame(height: 120)
            .clipShape(Rectangle())

            Image(systemName: "camera")
                .font(.title)
                .foregroundColor(.white)
        }
        .onTapGesture {
            viewModel.showImagePrickerForHeaderImage.toggle()
        }
        
    }
    
    var profileImage: some View {
        ZStack {
            
            Group {
                // プロフィールイメージを変更した場合、そのイメージを表示
                if let selectedImageFromImagePicker = self.viewModel.profileImage {
                    Image(uiImage: selectedImageFromImagePicker)
                        .resizable()
                        .scaledToFill()
                } else {
                    KFImage(URL(string: viewModel.user.profileImageUrl))
                        .resizable()
                        .scaledToFill()
                }
            }
            .frame(width: 70, height: 70)
            .clipShape(Circle())
            .overlay(
                Circle().strokeBorder(Color.white, lineWidth: 3)
            )
            
            Image(systemName: "camera")
                .font(.title2)
                .foregroundColor(.white)
        }
        .padding(.leading, 20)
        .offset(y: -20)
        .onTapGesture {
            viewModel.showImagePickerForProfileImage.toggle()
        }
    }
    
    var nameInput: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                Text("名前")
                    .fontWeight(.semibold)
                TextField("名前を追加", text: $viewModel.name)
                    .foregroundColor(.blue)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal)
            .padding(.vertical, 12)
        }
        .font(.subheadline)
    }
    
    var bioInput: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(alignment: .top) {
                Text("自己紹介")
                    .fontWeight(.semibold)
                TextField("プロフィールに自己紹介を追加", text: $viewModel.bio, axis: .vertical)
                    .foregroundColor(.blue)
                
                // 自己紹介のエリアは高さが60あり、TextFieldをtopに配置させるためのSpacer
                Spacer()
                    .frame(width: 1)
                    .frame(minHeight: 60)
            }
            .padding(.horizontal)
            .padding(.vertical, 12)
            
        }
        .font(.subheadline)
    }
    
    var loactionInput: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                Text("場所")
                    .fontWeight(.semibold)
                TextField("場所を追加", text: $viewModel.location)
                    .foregroundColor(.blue)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal)
            .padding(.vertical, 12)
        }
        .font(.subheadline)
    }
    
    var webUrlInput: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                Text("Web")
                    .fontWeight(.semibold)
                TextField("Webサイトを追加", text: $viewModel.webUrl)
                    .keyboardType(.URL)
                    .foregroundColor(.blue)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal)
            .padding(.vertical, 12)
        }
        .font(.subheadline)
    }
}

#Preview {
    ProfileEditView(user: .init(username: "username",
                                profileImageUrl: "profileImageUrl",
                                profileHeaderImageUrl: "profileHeaderImageUrl",
                                email: "email",
                                bio: "",
                                location: "",
                                webUrl: ""))
}
