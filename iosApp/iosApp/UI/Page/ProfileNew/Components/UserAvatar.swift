import Generated
import Kingfisher
import SwiftUI

struct UserAvatar: View {
    @Environment(\.appSettings) private var appSettings
    let data: String
    var size: CGFloat = 48

    var body: some View {
        let shape = switch appSettings.appearanceSettings.avatarShape {
        case .circle: RoundedRectangle(cornerRadius: size)
        case .square: RoundedRectangle(cornerRadius: 8)
        }
        KFImage(URL(string: data))
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(shape)
    }
}

func userAvatarPlaceholder(size: CGFloat = 28) -> some View {
    Image(systemName: "person.circle")
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: size, height: size)
        .foregroundColor(Color(Asset.Color.State.deactive.name))
}
