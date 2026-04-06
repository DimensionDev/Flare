import SwiftUI
import KotlinSharedUI

struct AvatarView: View {
    @Environment(\.appearanceSettings.avatarShape) private var avatarShape
    let data: String
    var body: some View {
        NetworkImage(data: data)
            .clipShape(avatarShape == .circle ? AnyShape(.circle) : AnyShape(.rect(cornerRadius: 8)))
    }
}
