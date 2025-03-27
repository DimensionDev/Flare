import SwiftUI

struct FLNewSideMenu<Menu: View, Content: View>: View {
    @Binding var isOpen: Bool
    let menu: Menu
    let content: Content

    private let menuWidth: CGFloat = UIScreen.main.bounds.width * 0.7
    private let animationDuration: Double = 0.3
    private let springDampingRatio: Double = 0.8
    private let springVelocity: Double = 0.5

    init(isOpen: Binding<Bool>, menu: Menu, content: Content) {
        _isOpen = isOpen
        self.menu = menu
        self.content = content
    }

    var body: some View {
        ZStack(alignment: .leading) {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .offset(x: isOpen ? menuWidth : 0)
                .animation(
                    .spring(
                        response: animationDuration,
                        dampingFraction: springDampingRatio,
                        blendDuration: springVelocity
                    ),
                    value: isOpen
                )

            if isOpen {
                Color.black
                    .opacity(0.3)
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
                        }
                    }
                    .transition(.opacity)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .zIndex(1)
            }

            menu
                .frame(width: menuWidth)
                .frame(maxHeight: .infinity)
                .offset(x: isOpen ? 0 : -menuWidth)
                .animation(
                    .spring(
                        response: animationDuration,
                        dampingFraction: springDampingRatio,
                        blendDuration: springVelocity
                    ),
                    value: isOpen
                )
                .clipped()
                .shadow(
                    color: Color.black.opacity(isOpen ? 0.2 : 0),
                    radius: 10,
                    x: 5,
                    y: 0
                )
                .zIndex(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(UIColor.systemBackground))
        .ignoresSafeArea()
    }
}
