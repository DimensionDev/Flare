import SwiftUI

// 定义所有可用的主题名称
public enum ColorSetName: String, CaseIterable, Identifiable {
    case flareLight
    case flareDark
    case system
    case snowfall
    case breezy
    case ember
    case sunset
    case carbon
    case nord
    case dracula
    case minuit
    case noir

    public var id: String { rawValue }

    public var localizedName: String {
        switch self {
        case .flareLight: "浅色主题"
        case .flareDark: "深色主题"
        case .system: "系统主题"
        case .snowfall: "雪落"
        case .breezy: "清风"
        case .ember: "余烬"
        case .sunset: "日落"
        case .carbon: "碳黑"
        case .nord: "北欧"
        case .dracula: "德古拉"
        case .minuit: "午夜"
        case .noir: "黑色电影"
        }
    }

    // 获取对应的ColorSet实例
    public var set: any ColorSet {
        switch self {
        case .flareLight: FlareColorSet()
        case .flareDark: DimColorSet()
        case .system: SystemColorSet()
        case .snowfall: SnowfallColorSet()
        case .breezy: BreezyColorSet()
        case .ember: EmberColorSet()
        case .sunset: SunsetColorSet()
        case .carbon: CarbonColorSet()
        case .nord: NordColorSet()
        case .dracula: DraculaColorSet()
        case .minuit: MinuitColorSet()
        case .noir: NoirColorSet()
        }
    }
}
