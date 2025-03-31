import SwiftUI

struct FLNewSideMenu<Menu: View, Content: View>: View {
    @Binding var isOpen: Bool
    @EnvironmentObject var appState: FlareAppState
    
    let menu: Menu
    let content: Content

    private let menuWidth: CGFloat = UIScreen.main.bounds.width * 0.7
    private let animationDuration: Double = 0.25
    private let springDampingRatio: Double = 0.7
    private let springVelocity: Double = 0.2

    init(isOpen: Binding<Bool>, menu: Menu, content: Content) {
        _isOpen = isOpen
        self.menu = menu
        self.content = content
    }

    var body: some View {
        ZStack(alignment: .leading) {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            if appState.menuProgress > 0.01 {
                Color.black
                    .opacity(min(0.3 * appState.menuProgress, 0.3))
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(
                            .spring(
                                response: animationDuration,
                                dampingFraction: springDampingRatio,
                                blendDuration: springVelocity
                            )
                        ) {
                            isOpen = false
                            appState.menuProgress = 0
                        }
                    }
                    .animation(nil, value: appState.menuProgress)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .zIndex(1)
            }

            menu
                .frame(width: menuWidth)
                .frame(maxHeight: .infinity)
                .mask(
                    Rectangle()
                        .offset(x: appState.menuProgress * menuWidth - menuWidth)
                )
                .offset(x: -menuWidth * (1 - appState.menuProgress))
                .animation(nil, value: appState.menuProgress)
                .clipped()
                .shadow(
                    color: appState.menuProgress > 0.3 ? Color.black.opacity(0.2) : .clear,
                    radius: 8,
                    x: 5,
                    y: 0
                )
                .zIndex(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(UIColor.systemBackground))
        .ignoresSafeArea()
        .onChange(of: isOpen) { newValue in
            if (appState.menuProgress < 0.1 && newValue) || (appState.menuProgress > 0.9 && !newValue) {
                withAnimation(.spring(
                    response: animationDuration,
                    dampingFraction: springDampingRatio,
                    blendDuration: springVelocity
                )) {
                    appState.menuProgress = newValue ? 1.0 : 0.0
                }
            }
        }
    }
}
