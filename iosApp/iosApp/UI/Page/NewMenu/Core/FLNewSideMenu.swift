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
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                content
                    .frame(width: geometry.size.width, height: geometry.size.height)
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
                        .frame(width: geometry.size.width, height: geometry.size.height)
                }

                menu
                    .frame(width: menuWidth, height: geometry.size.height)
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
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .background(Color(UIColor.systemBackground))
        }
        .ignoresSafeArea()
    }
}
