import Generated
import Kingfisher
import SwiftUI

struct UserAvatar: View, Equatable {
    @Environment(\.appSettings) private var appSettings

    let data: String
    var size: CGFloat = 48

    static func == (lhs: UserAvatar, rhs: UserAvatar) -> Bool {
        return lhs.data == rhs.data && lhs.size == rhs.size
    }

    private var avatarShape: some Shape {
        switch appSettings.appearanceSettings.avatarShape {
        case .circle: RoundedRectangle(cornerRadius: size)
        case .square: RoundedRectangle(cornerRadius: 8)
        }
    }

    private var avatarURL: URL? {
        URL(string: data)
    }

    var body: some View {
        KFImage(avatarURL)
         .placeholder {
                            Rectangle()
                                .foregroundColor(.gray.opacity(0.2))
                        }
            .flareTimelineAvatar(size: CGSize(width: size, height: size))
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(avatarShape)
    }
}

struct UserAvatarPlaceholder: View {
    @Environment(FlareTheme.self) private var theme

    var size: CGFloat = 28
    var body: some View {
        Image(systemName: "person.circle.fill")
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: size, height: size)
            .foregroundColor(theme.tintColor)
    }
}
