import SwiftUI
import KotlinSharedUI

struct UserListView: View {
    let data: UiTimeline.ItemContentUserList
    var body: some View {
        VStack {
            ScrollView(.horizontal) {
                HStack {
                    ForEach(data.users, id: \.key) { user in
                        UserCompatView(data: user)
                            .frame(width: 280)
                            .clipShape(.rect(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(.separator), lineWidth: 1)
                            )
                    }
                }
            }
            
            if let status = data.status {
                VStack {
                    StatusView(data: status, isQuote: true)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(.rect(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(.separator), lineWidth: 1)
                )
            }
        }
    }
}
