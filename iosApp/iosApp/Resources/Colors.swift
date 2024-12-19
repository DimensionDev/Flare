import SwiftUI
import UIKit

public enum Colors {
    public enum State {
        public static let active = UIColor(light: UIColor(red: 29/255, green: 161/255, blue: 242/255, alpha: 1.0),
                                          dark: UIColor(red: 29/255, green: 155/255, blue: 240/255, alpha: 1.0))
        public static let bookmarkActive = UIColor(light: UIColor(red: 255/255, green: 82/255, blue: 82/255, alpha: 1.0),
                                          dark: UIColor(red: 255/255, green: 82/255, blue: 82/255, alpha: 1.0))
        public static let likeActive = UIColor(light: UIColor(red: 255/255, green: 64/255, blue: 129/255, alpha: 1.0),
                                          dark: UIColor(red: 255/255, green: 64/255, blue: 129/255, alpha: 1.0))
        public static let retweetActive = UIColor(light: UIColor(red: 139/255, green: 195/255, blue: 74/255, alpha: 1.0),
                                          dark: UIColor(red: 104/255, green: 159/255, blue: 56/255, alpha: 1.0))
        public static let secondActive = UIColor(light: UIColor(red: 83/255, green: 100/255, blue: 113/255, alpha: 1.0),
                                                dark: UIColor(red: 110/255, green: 118/255, blue: 125/255, alpha: 1.0))
        public static let deactive = UIColor(light: UIColor(red: 139/255, green: 152/255, blue: 165/255, alpha: 1.0),
                                            dark: UIColor(red: 51/255, green: 54/255, blue: 57/255, alpha: 1.0))
        public static let deactiveDarker = UIColor(light: UIColor(red: 101/255, green: 119/255, blue: 134/255, alpha: 1.0),
                                                  dark: UIColor(red: 39/255, green: 44/255, blue: 48/255, alpha: 1.0))
        
        public static var swiftUIActive: Color { Color(uiColor: active) }
        public static var swiftUIBookmarkActive: Color { Color(uiColor: bookmarkActive) }
        public static var swiftUILikeActive: Color { Color(uiColor: likeActive) }
        public static var swiftUIRetweetActive: Color { Color(uiColor: retweetActive) }
        public static var swiftUISecondActive: Color { Color(uiColor: secondActive) }
        public static var swiftUIDeactive: Color { Color(uiColor: deactive) }
        public static var swiftUIDeactiveDarker: Color { Color(uiColor: deactiveDarker) }
    }
    
    public enum Text {
        public static let primary = UIColor(light: UIColor(red: 15/255, green: 20/255, blue: 25/255, alpha: 1.0),
                                           dark: UIColor(red: 231/255, green: 233/255, blue: 234/255, alpha: 1.0))
        public static let secondary = UIColor(light: UIColor(red: 83/255, green: 100/255, blue: 113/255, alpha: 1.0),
                                             dark: UIColor(red: 139/255, green: 152/255, blue: 165/255, alpha: 1.0))
        public static let tertiary = UIColor(light: UIColor(red: 139/255, green: 152/255, blue: 165/255, alpha: 1.0),
                                            dark: UIColor(red: 113/255, green: 118/255, blue: 123/255, alpha: 1.0))
        
        public static var swiftUIPrimary: Color { Color(uiColor: primary) }
        public static var swiftUISecondary: Color { Color(uiColor: secondary) }
        public static var swiftUITertiary: Color { Color(uiColor: tertiary) }
    }
    
    public enum Link {
        public static let hyperlink = State.active
        public static let mention = hyperlink
        public static let hashtag = hyperlink
        public static let cashtag = hyperlink
        
        public static var swiftUIHyperlink: Color { Color(uiColor: hyperlink) }
        public static var swiftUIMention: Color { Color(uiColor: mention) }
        public static var swiftUIHashtag: Color { Color(uiColor: hashtag) }
        public static var swiftUICashtag: Color { Color(uiColor: cashtag) }
    }
    
    public enum Background {
        public static let primary = UIColor(light: UIColor(red: 255/255, green: 255/255, blue: 255/255, alpha: 1.0),
                                           dark: UIColor(red: 0/255, green: 0/255, blue: 0/255, alpha: 1.0))
        public static let secondary = UIColor(light: UIColor(red: 247/255, green: 249/255, blue: 249/255, alpha: 1.0),
                                             dark: UIColor(red: 22/255, green: 24/255, blue: 28/255, alpha: 1.0))
        public static let tertiary = UIColor(light: UIColor(red: 239/255, green: 243/255, blue: 244/255, alpha: 1.0),
                                            dark: UIColor(red: 39/255, green: 44/255, blue: 48/255, alpha: 1.0))
        
        public static var swiftUIPrimary: Color { Color(uiColor: primary) }
        public static var swiftUISecondary: Color { Color(uiColor: secondary) }
        public static var swiftUITertiary: Color { Color(uiColor: tertiary) }
    }
}

private extension UIColor {
    convenience init(light: UIColor, dark: UIColor) {
        self.init { traitCollection in
            switch traitCollection.userInterfaceStyle {
            case .dark:
                return dark
            default:
                return light
            }
        }
    }
}
