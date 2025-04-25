//
//  AuthHeaderView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import SwiftUI

struct AuthHeaderView: View {
    
    let title: String
    var cancelTapped: () -> Void
    
    init(title: String, cancelTapped: @escaping () -> Void) {
        self.title = title
        self.cancelTapped = cancelTapped
    }

    var body: some View {
        
        VStack(spacing: 0) {
            // HeaderView
            ZStack {
                HStack {
                    Button {
                        cancelTapped()
                    } label: {
                        Text("キャンセル")
                            .foregroundStyle(.black)
                    }
                    Spacer()
                }
                
                xLogoSmall
            }
            .padding(.horizontal)
            .padding(.top, 10)
            
            Text(title)
                .multilineTextAlignment(.leading)
                .lineSpacing(8)
                .font(.system(size: 24))
                .fontWeight(.heavy)
                .padding(.top, 40)
                .padding(.horizontal)
        }
        .padding(.bottom, 40)
        
    }
}

#Preview {
    AuthHeaderView(title: "ログイン", cancelTapped: {})
}
