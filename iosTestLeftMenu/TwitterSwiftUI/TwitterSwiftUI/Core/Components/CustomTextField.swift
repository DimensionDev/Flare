//
//  CustomTextField.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/24.
//

import SwiftUI

struct CustomTextField: View {
    
    let imageName: String
    let placeholdeer: String
    var isSecureField: Bool = false
    @Binding var text: String
    
    var body: some View {
        VStack {
            HStack {
                Image(systemName: imageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .foregroundColor(Color(.darkGray))
                
                if isSecureField {
                    SecureField(placeholdeer, text: $text)
                } else {
                    TextField(placeholdeer, text: $text)
                }
            }
            
            Divider()
                .background(Color(.darkGray))
        }
    }
}

#Preview {
    CustomTextField(imageName: "envelope",
                    placeholdeer: "Email",
                    text: .constant(""))
}
