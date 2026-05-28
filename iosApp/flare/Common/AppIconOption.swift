import Foundation

struct AppIconOption: Identifiable {
    let title: String
    let alternateIconName: String?
    let previewImageName: String

    var id: String {
        alternateIconName ?? "AppIcon"
    }
}

extension AppIconOption {
    static let all: [AppIconOption] = [
        .init(title: "Black", alternateIconName: "AppIcon_black", previewImageName: "app_icon_preview_black"),
        .init(title: "Blue", alternateIconName: "AppIcon_blue", previewImageName: "app_icon_preview_blue"),
        .init(title: "Cyan", alternateIconName: "AppIcon_cyan", previewImageName: "app_icon_preview_cyan"),
        .init(title: "Light Blue", alternateIconName: "AppIcon_light_blue", previewImageName: "app_icon_preview_light_blue"),
        .init(title: "Orange", alternateIconName: "AppIcon_orange", previewImageName: "app_icon_preview_orange"),
        .init(title: "Red", alternateIconName: "AppIcon_red", previewImageName: "app_icon_preview_red"),
        .init(title: "Teal", alternateIconName: "AppIcon_teal", previewImageName: "app_icon_preview_teal"),
        .init(title: "White", alternateIconName: "AppIcon_white", previewImageName: "app_icon_preview_white"),
        .init(title: "Yellow", alternateIconName: "AppIcon_yellow", previewImageName: "app_icon_preview_yellow"),
        .init(title: "Default", alternateIconName: nil, previewImageName: "app_icon_preview_default"),
    ]

    static func previewImageName(for alternateIconName: String?) -> String {
        all.first { $0.alternateIconName == alternateIconName }?.previewImageName ?? "app_icon_preview_default"
    }
}
