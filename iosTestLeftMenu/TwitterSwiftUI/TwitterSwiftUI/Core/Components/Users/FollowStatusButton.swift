//
//  FollowStatusButton.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/22.
//

import SwiftUI

enum FollowStatus: Int, CaseIterable, Identifiable {
    case unfollowing
    case following
    
    var title: String {
        switch self {
        case .unfollowing: "フォローする"
        case .following: "フォロー中"
        }
    }
    
    var id: Int { self.rawValue }
}

struct FollowStatusButton: View {
    
    @Binding var status: FollowStatus
    var buttonTapped: () -> Void
    
    init(status: Binding<FollowStatus>, buttonTapped: @escaping () -> Void) {
        _status = status
        self.buttonTapped = buttonTapped
    }
    
    var body: some View {
        Button {
            buttonTapped()
        } label: {
            Text(status.title)
                .font(.footnote).bold()
                .frame(width: 140, height: 28)
                .foregroundColor(status == .unfollowing ? .white : .black)
                .background(
                    Color(status == .unfollowing ? .black : .clear)
                        .cornerRadius(20)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.gray, lineWidth: 0.75)
                )
        }
    }
}

#Preview {
    FollowStatusButton(status: .constant(.unfollowing), buttonTapped: {})
}
