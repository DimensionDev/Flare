//
//  NewMessageView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/15.
//

import SwiftUI

struct NewMessageView: View {
    
    @Environment(\.dismiss) var dismiss
    
    @StateObject var viewModel: NewMessageViewModel
    
    var userSelectCompletion: (User) -> Void
    
    init(userSelectCompletion: @escaping (User) -> Void) {
        self.userSelectCompletion = userSelectCompletion
        _viewModel = .init(wrappedValue: NewMessageViewModel())
    }
    
    var body: some View {
        VStack {
            
            header
            
            textInputForm
            
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack {
                    ForEach(viewModel.users) { user in
                        UserRowView(user: user)
                            .onTapGesture {
                                userSelectCompletion(user)
                                dismiss()
                            }
                    }
                }
            }
        }
    }
}

extension NewMessageView {
    var header: some View {
        VStack {
            ZStack {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Text("キャンセル")
                    }
                    Spacer()
                }
                
                Text("新しいメッセージ")
                    .font(.headline)
                    .bold()
            }
            .foregroundStyle(.black)
            .padding(.horizontal)
            .padding(.vertical, 6)
            .padding(.top, 5)
            Divider()
        }
    }
    
    var textInputForm: some View {
        VStack {
            HStack {
                Text("送信先：")
                    .font(.footnote)
                    .foregroundStyle(.black)
                
                TextField("", text: $viewModel.searchedText)
                    .textInputAutocapitalization(.never)
            }
            .padding(.horizontal)
            .padding(.vertical, 5)
            
            Divider()
        }
    }
}

#Preview {
    NewMessageView(userSelectCompletion: { _ in })
}
