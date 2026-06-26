import SwiftUI
import KotlinSharedUI

public struct AvatarView: View {
    @Environment(\.timelineAppearance.avatarShape) private var avatarShape
    private let data: String?
    private let customHeader: [String: String]?

    public init(data: String?, customHeader: [String: String]? = nil) {
        self.data = data
        self.customHeader = customHeader
    }

    public var body: some View {
        NetworkImage(data: data, customHeader: customHeader)
            .clipShape(avatarShape == .circle ? AnyShape(.circle) : AnyShape(.rect(cornerRadius: 8)))
    }
}
