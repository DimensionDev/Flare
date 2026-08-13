import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

public struct PluginPlatformIcon: View {
    private let iconURL: String?
    private let fallback: UiIcon
    private let size: CGFloat

    public init(iconURL: String?, fallback: UiIcon, size: CGFloat) {
        self.iconURL = iconURL
        self.fallback = fallback
        self.size = size
    }

    public var body: some View {
        if let iconURL, !iconURL.isEmpty {
            NetworkImage(data: iconURL, contentMode: .fit)
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: size * 0.2))
        } else {
            Image(fontAwesome: fallback.fontAwesomeIcon)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        }
    }
}
