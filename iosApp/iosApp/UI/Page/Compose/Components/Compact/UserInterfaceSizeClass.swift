import Foundation
import SwiftUI

#if os(macOS)
enum UserInterfaceSizeClass {
    case compact
    case regular
}

struct HorizontalSizeClassEnvironmentKey: EnvironmentKey {
    static let defaultValue: UserInterfaceSizeClass = .regular
}
struct VerticalSizeClassEnvironmentKey: EnvironmentKey {
    static let defaultValue: UserInterfaceSizeClass = .regular
}

extension EnvironmentValues {
    var horizontalSizeClass: UserInterfaceSizeClass {
        get { return self[HorizontalSizeClassEnvironmentKey.self] }
        set { self[HorizontalSizeClassEnvironmentKey.self] = newValue }
    }
    var verticalSizeClass: UserInterfaceSizeClass {
        get { return self[VerticalSizeClassEnvironmentKey.self] }
        set { self[VerticalSizeClassEnvironmentKey.self] = newValue }
    }
}

struct ProvideWindowSizeClass<Child>: View where Child: View {
    let content: () -> Child
    var body: some View {
        GeometryReader { proxy in
            let horizontalSizeClass: UserInterfaceSizeClass = proxy.size.width < 768 ? .compact : .regular
            let verticalSizeClass: UserInterfaceSizeClass = proxy.size.height < 768 ? .compact : .regular
            content()
                .environment(\.horizontalSizeClass, horizontalSizeClass)
                .environment(\.verticalSizeClass, verticalSizeClass)
        }
    }
}

#endif
