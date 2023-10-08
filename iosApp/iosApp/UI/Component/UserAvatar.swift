import SwiftUI

struct UserAvatar: View {
    let data: String
    var size: CGFloat = 48
    var body: some View {
        AsyncImage(url: URL(string: data)){ image in
            image.image?.resizable().scaledToFit()
        }
            .frame(width: size, height: size)
            .clipShape(Circle())
    }
}

#Preview {
    UserAvatar(data: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg")
}
