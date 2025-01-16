import SwiftUI

#if DEBUG
    public extension View {
        @ViewBuilder
        func enableInjection() -> some View {
            onAppear {
                var injectionBundle: Bundle? = nil
                #if os(iOS)
                    injectionBundle = Bundle(path: "/Applications/InjectionIII.app/Contents/Resources/iOSInjection.bundle")
                #elseif os(macOS)
                    injectionBundle = Bundle(path: "/Applications/InjectionIII.app/Contents/Resources/macOSInjection.bundle")
                #endif
                injectionBundle?.load()
            }
        }
    }
#endif
