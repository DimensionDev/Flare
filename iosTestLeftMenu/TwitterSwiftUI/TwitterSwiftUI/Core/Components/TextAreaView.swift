//
//  TextAreaView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI

struct TextAreaView: View {
    
    @Binding var text: String
    let placeHolder: String
    
    init(_ placeHolder: String, text: Binding<String>) {
        self.placeHolder = placeHolder
        self._text = text
        UITextView.appearance().backgroundColor = .clear
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            
            TextEditor(text: $text)
                .padding(4)
            
            if text.isEmpty {
                Text(placeHolder)
                    .foregroundStyle(Color(.placeholderText))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 12)
            }
        }
        .font(.body)
    }
}
