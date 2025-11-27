import SwiftUI
import KotlinSharedUI

struct AvatarView: View {
    @Environment(\.appearanceSettings.avatarShape) private var avatarShape
    let data: String
    var body: some View {
        NetworkImage(data: data)
            .if(avatarShape == .circle, if: { view in
                view.clipShape(.circle)
            }, else: { view in
                view.clipShape(.rect(cornerRadius: 8))
            })
    }
}
