import SwiftUI
import NetworkImage

struct UserAvatar: View {
    @Environment(\.appSettings) private var appSettings
    let data: String
    var size: CGFloat = 48
    var body: some View {
        NetworkImage(url: URL(string: data)) { image in
            image.resizable().scaledToFit()
        }
        .frame(width: size, height: size)
        .if(appSettings.appearanceSettings.avatarShape == AvatarShape.circle, transform: { view in
            view.clipShape(Circle())
        })
        .if(appSettings.appearanceSettings.avatarShape == AvatarShape.square, transform: { view in
            view.clipShape(RoundedRectangle(cornerRadius: 8))
        })
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
