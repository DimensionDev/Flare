import Combine
import SwiftUI
import UIKit

@MainActor
@Observable
public final class FlareTheme {
  final class ThemeStorage {
    enum ThemeKey: String {
      case colorScheme, tint, label, primaryBackground, secondaryBackground
      case avatarPosition2, avatarShape2, statusActionsDisplay, statusDisplayStyle
      case selectedSet, selectedScheme
      case followSystemColorSchme
      case displayFullUsernameTimeline
      case lineSpacing
      case statusActionSecondary
      case contentGradient
      case compactLayoutPadding
    }

    @AppStorage("is_previously_set") public var isThemePreviouslySet: Bool = false
    @AppStorage(ThemeKey.selectedScheme.rawValue) public var selectedScheme: ColorScheme = .dark
    @AppStorage(ThemeKey.tint.rawValue) public var tintColor: SwiftUI.Color = SwiftUI.Color.flareTint
    @AppStorage(ThemeKey.primaryBackground.rawValue) public var primaryBackgroundColor: SwiftUI.Color = SwiftUI.Color.flarePrimaryBackground
    @AppStorage(ThemeKey.secondaryBackground.rawValue) public var secondaryBackgroundColor: SwiftUI.Color = SwiftUI.Color.flareSecondaryBackground
    @AppStorage(ThemeKey.label.rawValue) public var labelColor: SwiftUI.Color = SwiftUI.Color.flareLabel
    @AppStorage(ThemeKey.avatarPosition2.rawValue) var avatarPosition: AvatarPosition = .leading
    @AppStorage(ThemeKey.avatarShape2.rawValue) var avatarShape: AvatarShape = .circle
    @AppStorage(ThemeKey.selectedSet.rawValue) var storedSet: ColorSetName = .iceCubeDark
    @AppStorage(ThemeKey.statusActionsDisplay.rawValue) public var statusActionsDisplay:
      StatusActionsDisplay = .full
    @AppStorage(ThemeKey.statusDisplayStyle.rawValue) public var statusDisplayStyle:
      StatusDisplayStyle = .large
    @AppStorage(ThemeKey.followSystemColorSchme.rawValue) public var followSystemColorScheme: Bool =
      true
    @AppStorage(ThemeKey.displayFullUsernameTimeline.rawValue) public var displayFullUsername:
      Bool = false
    @AppStorage(ThemeKey.lineSpacing.rawValue) public var lineSpacing: Double = 1.2
    @AppStorage(ThemeKey.statusActionSecondary.rawValue) public var statusActionSecondary:
      StatusActionSecondary = .share
    @AppStorage(ThemeKey.contentGradient.rawValue) public var showContentGradient: Bool = true
    @AppStorage(ThemeKey.compactLayoutPadding.rawValue) public var compactLayoutPadding: Bool = true
    @AppStorage("font_size_scale") public var fontSizeScale: Double = 1
    @AppStorage("chosen_font") public var chosenFontData: Data?

    init() {}
  }

  public enum FontState: Int, CaseIterable {
    case system
    case openDyslexic
    case hyperLegible
    case SFRounded
    case custom

    public var title: String {
      switch self {
      case .system:
        "System Font"
      case .openDyslexic:
        "Open Dyslexic"
      case .hyperLegible:
        "Hyper Legible"
      case .SFRounded:
        "SF Rounded"
      case .custom:
        "Custom Font"
      }
    }
  }

  public enum AvatarPosition: String, CaseIterable {
    case leading, top

    public var description: String {
      switch self {
      case .leading:
        "Leading"
      case .top:
        "Top"
      }
    }
  }

  public enum StatusActionSecondary: String, CaseIterable {
    case share, bookmark

    public var description: String {
      switch self {
      case .share:
        "Share"
      case .bookmark:
        "Bookmark"
      }
    }
  }

  public enum AvatarShape: String, CaseIterable {
    case circle, rounded

    public var description: String {
      switch self {
      case .circle:
        "Circle"
      case .rounded:
        "Rounded"
      }
    }
  }

  public enum StatusActionsDisplay: String, CaseIterable {
    case full, discret, none

    public var description: String {
      switch self {
      case .full:
        "All"
      case .discret:
        "Only Buttons"
      case .none:
        "No Buttons"
      }
    }
  }

  public enum StatusDisplayStyle: String, CaseIterable {
    case large, medium, compact

    public var description: String {
      switch self {
      case .large:
        "Large"
      case .medium:
        "Medium"
      case .compact:
        "Compact"
      }
    }
  }

  private var _cachedChoosenFont: UIFont?
  public var chosenFont: UIFont? {
    get {
      if let _cachedChoosenFont {
        return _cachedChoosenFont
      }
      guard let chosenFontData,
        let font = try? NSKeyedUnarchiver.unarchivedObject(
          ofClass: UIFont.self, from: chosenFontData)
      else { return nil }

      _cachedChoosenFont = font
      return font
    }
    set {
      if let font = newValue,
        let data = try? NSKeyedArchiver.archivedData(
          withRootObject: font, requiringSecureCoding: false)
      {
        chosenFontData = data
      } else {
        chosenFontData = nil
      }
      _cachedChoosenFont = nil
    }
  }

  let themeStorage = ThemeStorage()

  public var isThemePreviouslySet: Bool {
    get { themeStorage.isThemePreviouslySet }
    set { themeStorage.isThemePreviouslySet = newValue }
  }

  public var selectedScheme: ColorScheme {
    get { themeStorage.selectedScheme }
    set { themeStorage.selectedScheme = newValue }
  }

  public var tintColor: SwiftUI.Color {
    get { themeStorage.tintColor }
    set {
      themeStorage.tintColor = newValue
      computeContrastingTintColor()
    }
  }

  public var primaryBackgroundColor: SwiftUI.Color {
    get { themeStorage.primaryBackgroundColor }
    set {
      themeStorage.primaryBackgroundColor = newValue
      computeContrastingTintColor()
    }
  }

  public var secondaryBackgroundColor: SwiftUI.Color {
    get { themeStorage.secondaryBackgroundColor }
    set { themeStorage.secondaryBackgroundColor = newValue }
  }

  public var labelColor: SwiftUI.Color {
    get { themeStorage.labelColor }
    set {
      themeStorage.labelColor = newValue
      computeContrastingTintColor()
    }
  }

  public private(set) var contrastingTintColor: SwiftUI.Color = SwiftUI.Color.red

  // 设置 contrastingTintColor 为 labelColor 或 primaryBackgroundColor，取决于哪个与 tintColor 对比度更好
  private func computeContrastingTintColor() {
    func luminance(_ color: SwiftUI.Color.Resolved) -> Float {
      return 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    }

    let resolvedTintColor = tintColor.resolve(in: .init())
    let resolvedLabelColor = labelColor.resolve(in: .init())
    let resolvedPrimaryBackgroundColor = primaryBackgroundColor.resolve(in: .init())

    let tintLuminance = luminance(resolvedTintColor)
    let labelLuminance = luminance(resolvedLabelColor)
    let primaryBackgroundLuminance = luminance(resolvedPrimaryBackgroundColor)

    if abs(tintLuminance - labelLuminance) > abs(tintLuminance - primaryBackgroundLuminance) {
      contrastingTintColor = labelColor
    } else {
      contrastingTintColor = primaryBackgroundColor
    }
  }

  public var avatarPosition: AvatarPosition {
    get { themeStorage.avatarPosition }
    set { themeStorage.avatarPosition = newValue }
  }

  public var avatarShape: AvatarShape {
    get { themeStorage.avatarShape }
    set { themeStorage.avatarShape = newValue }
  }

  private var storedSet: ColorSetName {
    get { themeStorage.storedSet }
    set { themeStorage.storedSet = newValue }
  }

  public var statusActionsDisplay: StatusActionsDisplay {
    get { themeStorage.statusActionsDisplay }
    set { themeStorage.statusActionsDisplay = newValue }
  }

  public var statusDisplayStyle: StatusDisplayStyle {
    get { themeStorage.statusDisplayStyle }
    set { themeStorage.statusDisplayStyle = newValue }
  }

  public var statusActionSecondary: StatusActionSecondary {
    get { themeStorage.statusActionSecondary }
    set { themeStorage.statusActionSecondary = newValue }
  }

  public var followSystemColorScheme: Bool {
    get { themeStorage.followSystemColorScheme }
    set { themeStorage.followSystemColorScheme = newValue }
  }

  public var displayFullUsername: Bool {
    get { themeStorage.displayFullUsername }
    set { themeStorage.displayFullUsername = newValue }
  }

  public var lineSpacing: Double {
    get { themeStorage.lineSpacing }
    set { themeStorage.lineSpacing = newValue }
  }

  public var fontSizeScale: Double {
    get { themeStorage.fontSizeScale }
    set { themeStorage.fontSizeScale = newValue }
  }

  public private(set) var chosenFontData: Data? {
    get { themeStorage.chosenFontData }
    set { themeStorage.chosenFontData = newValue }
  }

  public var showContentGradient: Bool {
    get { themeStorage.showContentGradient }
    set { themeStorage.showContentGradient = newValue }
  }

  public var compactLayoutPadding: Bool {
    get { themeStorage.compactLayoutPadding }
    set { themeStorage.compactLayoutPadding = newValue }
  }

  public var selectedSet: ColorSetName = .iceCubeDark

  public static let shared = FlareTheme()

  public func restoreDefault() {
    applySet(set: themeStorage.selectedScheme == .dark ? .iceCubeDark : .iceCubeLight)
    isThemePreviouslySet = true
    avatarPosition = .leading
    avatarShape = .circle
    storedSet = selectedSet
    statusActionsDisplay = .full
    statusDisplayStyle = .large
    followSystemColorScheme = true
    displayFullUsername = false
    lineSpacing = 1.2
    fontSizeScale = 1
    chosenFontData = nil
    statusActionSecondary = .share
    showContentGradient = true
    compactLayoutPadding = true
  }

  private init() {
    isThemePreviouslySet = themeStorage.isThemePreviouslySet
    selectedScheme = themeStorage.selectedScheme
    tintColor = themeStorage.tintColor
    primaryBackgroundColor = themeStorage.primaryBackgroundColor
    secondaryBackgroundColor = themeStorage.secondaryBackgroundColor
    labelColor = themeStorage.labelColor
    contrastingTintColor = SwiftUI.Color.red  // real work done in computeContrastingTintColor()
    avatarPosition = themeStorage.avatarPosition
    avatarShape = themeStorage.avatarShape
    storedSet = themeStorage.storedSet
    statusActionsDisplay = themeStorage.statusActionsDisplay
    statusDisplayStyle = themeStorage.statusDisplayStyle
    followSystemColorScheme = themeStorage.followSystemColorScheme
    displayFullUsername = themeStorage.displayFullUsername
    lineSpacing = themeStorage.lineSpacing
    fontSizeScale = themeStorage.fontSizeScale
    chosenFontData = themeStorage.chosenFontData
    statusActionSecondary = themeStorage.statusActionSecondary
    showContentGradient = themeStorage.showContentGradient
    compactLayoutPadding = themeStorage.compactLayoutPadding
    selectedSet = storedSet

    computeContrastingTintColor()
  }

  public static var allColorSet: [ColorSet] {
    [
      IceCubeDark(),
      IceCubeLight(),
      IceCubeNeonDark(),
      IceCubeNeonLight(),
      DesertDark(),
      DesertLight(),
      NemesisDark(),
      NemesisLight(),
      MediumLight(),
      MediumDark(),
      ConstellationLight(),
      ConstellationDark(),
      ThreadsLight(),
      ThreadsDark(),
    ]
  }

  public func applySet(set: ColorSetName) {
    selectedSet = set
    setColor(withName: set)
  }

  public func setColor(withName name: ColorSetName) {
    let colorSet = FlareTheme.allColorSet.filter { $0.name == name }.first ?? IceCubeDark()
    selectedScheme = colorSet.scheme
    tintColor = colorSet.tintColor
    primaryBackgroundColor = colorSet.primaryBackgroundColor
    secondaryBackgroundColor = colorSet.secondaryBackgroundColor
    labelColor = colorSet.labelColor
    storedSet = name
  }
} 