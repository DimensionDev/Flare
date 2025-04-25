//
//  View+Extensions.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import SwiftUI

extension View {
    
    var xLogoSmall: some View {
        Image("xlogo")
            .resizable()
            .scaledToFit()
            .frame(width: 24, height: 24)
    }
          
    var xLogo: some View {
        Image("xlogo")
            .resizable()
            .scaledToFit()
            .frame(width: 36, height: 36)
    }
    
    var xLogoLarge: some View {
        Image("xlogo")
            .resizable()
            .scaledToFit()
            .frame(width: 48, height: 48)
    }
}
