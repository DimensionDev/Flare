//
//  SideMenuOptionRowView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/23.
//

import SwiftUI

struct SideMenuOptionRowView: View {
    
    let type: SideMenuListType
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: type.imageName)
                .font(.headline)
                .foregroundColor(.gray)
            
            Text(type.title)
                .font(.subheadline)
                .foregroundStyle(.black)
        }
        .frame(height: 40)
    }
}
 
