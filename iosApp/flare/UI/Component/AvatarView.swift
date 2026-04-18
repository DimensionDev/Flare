import SwiftUI
import KotlinSharedUI

public struct AvatarView: View {
    @Environment(\.appearanceSettings.avatarShape) private var avatarShape
    public let data: String
    public var body: some View {
        NetworkImage(data: data)
            .clipShape(avatarShape == .circle ? AnyShape(.circle) : AnyShape(.rect(cornerRadius: 8)))
    }
}
