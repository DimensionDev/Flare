import SwiftUI

struct AvatarView: View {
    let data: String
    var body: some View {
        NetworkImage(data: data)
            .clipShape(.circle)
    }
}
