import SwiftUI
import KotlinSharedUI

struct AvatarView: View {
    @Environment(\.timelineAppearance.avatarShape) private var avatarShape
    let data: String?
    let customHeader: [String: String]?

    init(data: String?, customHeader: [String: String]? = nil) {
        self.data = data
        self.customHeader = customHeader
    }

    var body: some View {
        NetworkImage(data: data, customHeader: customHeader)
            .clipShape(avatarShape == .circle ? AnyShape(.circle) : AnyShape(.rect(cornerRadius: 8)))
    }
}
