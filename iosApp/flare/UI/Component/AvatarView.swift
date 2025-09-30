import SwiftUI
import KotlinSharedUI

struct AvatarView: View {
    @Environment(\.themeSettings) private var themeSettings
    let data: String
    var body: some View {
        NetworkImage(data: data)
            .if(themeSettings.appearanceSettings.avatarShape == .circle, if: { view in
                view.clipShape(.circle)
            }, else: { view in
                view.clipShape(.rect(cornerRadius: 8))
            })
    }
}
