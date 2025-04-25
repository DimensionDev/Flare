//
//  AuthSubmitButton.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

import SwiftUI

struct AuthSubmitButton: View {
    
    let title: String
    let width: CGFloat
    let buttonAction: () -> Void
    
    var body: some View {

        Button {
            buttonAction()
        } label: {
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
                .frame(width: width, height: 50)
                .background(.black)
                .clipShape(Capsule())
        }
        .shadow(color: .gray.opacity(0.5), radius: 5, x: 0, y: 0)
        .padding(.top, 20)
    }
}

#Preview {
    AuthSubmitButton(title: "Sign Up", width: 320) {
        print("tapped")
    }
}
