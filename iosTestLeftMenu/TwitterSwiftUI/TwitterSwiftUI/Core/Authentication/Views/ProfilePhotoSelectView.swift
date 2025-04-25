//
//  ProfilePhotoSelectView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

import SwiftUI

struct ProfilePhotoSelectView: View {
    
    @StateObject var viewModel: ProfilePhotoSelectViewModel
    
    init() {
        _viewModel = .init(wrappedValue: ProfilePhotoSelectViewModel())
    }

    var body: some View {
        
        VStack {
            AuthHeaderView(title: "Setup account.",
                           cancelTapped: { })
            
            Button {
                viewModel.showImagePicker.toggle()
            } label: {
                if let image = self.viewModel.image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 100, height: 100)
                        .clipShape(Circle())
                } else {
                    Image(systemName: "plus")
                        .font(.title)
                        .foregroundColor(.white)
                        .frame(width: 100, height: 100)
                        .background(Color(.systemBlue))
                        .clipShape(Circle())
                }
            }
            .padding(.vertical, 40)
            .sheet(isPresented: $viewModel.showImagePicker, content: {
                ImagePicker(selectedImage: $viewModel.image)
            })
            
            if let image = self.viewModel.image {
                Button {
                    viewModel.uploadProfileImage(image)
                } label: {
                    Text("Countinue")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(width: 340, height: 50)
                        .background(Color(.systemBlue))
                        .clipShape(Capsule())
                        .padding()
                }
            }
            
            Spacer()
        }
        .ignoresSafeArea()
    }
}

#Preview {
    ProfilePhotoSelectView()
}
