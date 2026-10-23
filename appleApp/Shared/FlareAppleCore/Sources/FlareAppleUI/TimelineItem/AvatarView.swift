import SwiftUI
import KotlinSharedUI

public struct AvatarView: View {
    @Environment(\.timelineAppearance.avatarShape) private var avatarShape
    private let data: UiMedia?

    public init(data: UiMedia?) {
        self.data = data
    }

    public var body: some View {
        NetworkImage(data: data)
            .clipShape(avatarShape == .circle ? AnyShape(.circle) : AnyShape(.rect(cornerRadius: 8)))
    }
}
