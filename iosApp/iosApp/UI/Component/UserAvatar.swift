import SwiftUI
import Kingfisher

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

func userAvatarPlaceholder(size: CGFloat = 48) -> some View {
    return UserAvatar(
        data: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg",
        size: size
    )
    .redacted(reason: .placeholder)
}

#Preview {
    UserAvatar(data: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg")
}
