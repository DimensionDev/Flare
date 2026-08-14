import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

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
            icon(url: iconURL)
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: size * 0.2))
        } else {
            fallbackIcon
        }
    }

    @ViewBuilder
    private func icon(url value: String) -> some View {
        if value.lowercased().hasPrefix("file://") {
            let path = String(value.dropFirst("file://".count))
            let url = URL(fileURLWithPath: path)
            #if os(iOS)
            if let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                fallbackIcon
            }
            #elseif os(macOS)
            if let image = NSImage(contentsOf: url) {
                Image(nsImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                fallbackIcon
            }
            #else
            fallbackIcon
            #endif
        } else {
            NetworkImage(data: value, contentMode: .fit)
        }
    }

    private var fallbackIcon: some View {
        Image(fontAwesome: fallback.fontAwesomeIcon)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
    }
}
