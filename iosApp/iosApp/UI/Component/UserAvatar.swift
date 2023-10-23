import SwiftUI
import NetworkImage

struct UserAvatar: View {
    let data: String
    var size: CGFloat = 48
    var body: some View {
        NetworkImage(url: URL(string: data)){ image in
            image.resizable().scaledToFit()
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

func UserAvatarPlaceholder(size: CGFloat = 48) -> some View {
    return UserAvatar(data: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", size: size).redacted(reason: .placeholder)
}

#Preview {
    UserAvatar(data: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg")
}
