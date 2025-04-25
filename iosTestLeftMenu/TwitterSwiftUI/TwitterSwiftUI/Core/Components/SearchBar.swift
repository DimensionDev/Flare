//
//  SearchBar.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/25.
//

import SwiftUI

struct SearchBar: View {
    
    @Binding var text: String
    
    var body: some View {
        HStack {
            TextField("検索", text: $text, axis: .vertical)
                .padding(6)
                .padding(.horizontal, 24)
                .background(Color(.systemGray6))
                .cornerRadius(20)
                .overlay(
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.gray)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.leading, 8)
                    }
                )
        }
        .padding(.horizontal, 4)
    }
}

#Preview {
    SearchBar(text: .constant(""))
        .previewLayout(.sizeThatFits)
}
