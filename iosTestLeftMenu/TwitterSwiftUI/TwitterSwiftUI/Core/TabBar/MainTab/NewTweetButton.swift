//
//  NewTweetButton.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/15.
//

import SwiftUI

struct NewTweetButton: View {
    
    @Binding var selectedTab: MainTabBarFilter
    @State private var beforeSelectedTab: MainTabBarFilter = .home
    
    @State private var isRotated: Bool = false
    @State private var scaleEffect: CGFloat = 1.0
    
    var buttonTapped: (MainTabBarFilter) -> Void
    
    init(selectedTab: Binding<MainTabBarFilter>,
         buttonCompletion: @escaping (MainTabBarFilter) -> Void)  {
        _selectedTab = selectedTab
        self.buttonTapped = buttonCompletion
    }
    
    var body: some View {
        Button {
            buttonTapped(selectedTab)
        } label: {
            if selectedTab == .messages {
                Image("Message")
                    .resizable()
                    .renderingMode(.template)
                    .foregroundColor(.white)
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .rotationEffect(Angle(degrees: -180))
                    .rotationEffect(Angle(degrees: isRotated ? 180 : 0))
                    .scaleEffect(scaleEffect)
            } else {
                Image(systemName: "plus")
                    .font(.title2)
                    .foregroundColor(.white)
                    .rotationEffect(Angle(degrees: isRotated ? 180 : 0))
                    .scaleEffect(scaleEffect)
            }
        }
        .onChange(of: selectedTab) { tab in
            // 他のタブ -> メッセージタブ時、アニメーション
            // メッセージタブ -> 他のタブ時、アニメーション
            if selectedTab == .messages
                || selectedTab != .messages && beforeSelectedTab == .messages {
                withAnimation {
                    isRotated.toggle()
                    scaleEffect = 1.3
                }
            }
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                withAnimation(.spring(response: 0.5)) {
                    scaleEffect = 1.0
                }
            }
            
            self.beforeSelectedTab = selectedTab
            
        }
        .background(
            Circle()
                .fill(Color(.systemBlue))
                .frame(width: 54, height: 54)
                .scaleEffect(scaleEffect)
        )
        .padding(.trailing, 36)
        .padding(.bottom, 70)
    }
}

#Preview {
    NewTweetButton(selectedTab: .constant(.home), buttonCompletion: { _ in })
}
