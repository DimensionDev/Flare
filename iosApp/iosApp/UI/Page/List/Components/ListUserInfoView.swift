import Kingfisher
import shared
import SwiftUI

struct ListUserInfoView: View {
    let avatar: String
    let name: String
    let size: CGFloat

    init(avatar: String, name: String, size: CGFloat = 16) {
        self.avatar = avatar
        self.name = name
        self.size = size
    }

    var body: some View {
        HStack(spacing: 4) {
            if let url = URL(string: avatar) {
                KFImage(url)
                    .placeholder {
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.circle.fill")
                    .resizable()
                    .frame(width: size, height: size)
                    .foregroundColor(.gray)
            }

            Text(name)
                .font(.caption)
                .foregroundColor(.gray)
                .lineLimit(1)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

func listUserInfoView(avatar: String, name: String) -> some View {
    ListUserInfoView(avatar: avatar, name: name)
}
