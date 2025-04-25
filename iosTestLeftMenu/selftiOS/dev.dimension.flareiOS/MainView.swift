import SwiftUI

struct MainView: View {
    // 从环境中获取 AppState
    @EnvironmentObject private var appState: AppState
    // 菜单宽度常量
    private let menuWidth: CGFloat = 250

    // --- 偏移量计算 (修改) ---
    private var currentMenuOffset: CGFloat {
        let baseOffset = appState.isMenuOpen ? 0 : -menuWidth
        let combinedOffset = baseOffset + appState.menuDragOffset
        return min(max(combinedOffset, -menuWidth), 0)
    }

    private var currentContentOffset: CGFloat {
        let baseOffset = appState.isMenuOpen ? menuWidth : 0
        let combinedOffset = baseOffset + appState.menuDragOffset
        return min(max(combinedOffset, 0), menuWidth)
    }

    // --- Helper for overlay opacity ---
    private func calculateOverlayOpacity() -> Double {
        // Opacity increases as content moves right, max 0.4
        let progress = currentContentOffset / menuWidth
        return max(0, min(progress * 0.4, 0.4))
    }

    var body: some View {
        // 使用 ZStack 叠放菜单和主内容
        ZStack(alignment: .leading) {
            // --- Main Content (Tab View) ---
            tabContentView
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemBackground))
                .offset(x: currentContentOffset)
                .zIndex(0) // Base layer

            // --- Dimming Overlay (Enhanced) ---
            // Show if menu is open OR if dragging is occurring from closed state
            if appState.isMenuOpen || appState.menuDragOffset > 0 {
                Color.black.opacity(calculateOverlayOpacity())
                    .contentShape(Rectangle()) // Ensure it catches taps
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .offset(x: currentContentOffset) // Follow content offset
                    .onTapGesture {
                         print("[MainView Overlay] Tapped. Closing menu.")
                        withAnimation(.interactiveSpring()) {
                            appState.isMenuOpen = false
                            // Offset reset will be handled by onChange
                        }
                    }
                    .zIndex(1) // Above content, below menu
                    .ignoresSafeArea() // Cover entire screen including safe areas
            }

            // --- Side Menu ---
            MenuView()
                .frame(width: menuWidth)
                .offset(x: currentMenuOffset)
                .zIndex(2) // Topmost layer
        }
        .contentShape(Rectangle()) // Allow ZStack to receive gestures
        // --- Animations (seem correct) ---
        .animation(.interactiveSpring(), value: appState.isMenuOpen)
        .animation(.interactiveSpring(), value: appState.menuDragOffset)
        .ignoresSafeArea(edges: .bottom) // Keep bottom safe area ignored

        // --- Global Drag Gesture (Reverted to Always-Attached with Internal Guards) ---
        .simultaneousGesture( // Always attach the gesture
            DragGesture(minimumDistance: 10)
                .onChanged { value in
                    // Use internal guard to only act when menu is open
                    guard appState.isMenuOpen else { return }

                    // This onChanged only triggers if the gesture is attached (i.e., menu is open)
                    // We are dragging from the open state (offset 0) towards closed (offset -menuWidth)
                    let horizontalTranslation = value.translation.width
                    // Only consider leftward drag (negative translation)
                    if horizontalTranslation <= 0 {
                        // Calculate the offset, clamped between -menuWidth and 0
                        let dragOffset = min(max(horizontalTranslation, -menuWidth), 0)
                        // Update the global state directly
                        appState.menuDragOffset = dragOffset
                        // print("[MainView Global Gesture] onChanged - Offset: \(dragOffset)")
                    }
                    // Ignore rightward drags when menu is fully open
                }
                .onEnded { value in
                    // Use internal guard to only act when menu is open
                    guard appState.isMenuOpen else { return }

                    // Use the *current* drag offset (which was updated in onChanged)
                    // and the predicted velocity to decide the final state.
                    let currentDragOffset = appState.menuDragOffset // Get the latest value
                    let predictedEndTranslation = value.predictedEndTranslation.width

                    let quickSwipeThreshold: CGFloat = -100
                    let dragCloseThreshold = -menuWidth * 0.4

                    // Decide whether to close or snap back open
                    if predictedEndTranslation < quickSwipeThreshold || currentDragOffset < dragCloseThreshold {
                        print("[MainView Global Gesture] onEnded - Closing menu.")
                        withAnimation(.interactiveSpring()) {
                            appState.isMenuOpen = false
                            // menuDragOffset reset is handled by .onChange(of: appState.isMenuOpen)
                        }
                    } else {
                        print("[MainView Global Gesture] onEnded - Snapping back to open.")
                        // If not closing, we need to animate menuDragOffset back to 0
                        // since isMenuOpen remains true. The .onChange won't trigger for offset reset.
                        withAnimation(.interactiveSpring()) {
                            appState.menuDragOffset = 0
                        }
                    }
                }
        )
        // --- State Consistency ---
        .onChange(of: appState.isMenuOpen) { _, isOpen in
            // This still correctly resets the offset when the state *changes* to closed.
            if !isOpen && appState.menuDragOffset != 0 {
                print("[MainView onChange] isMenuOpen changed to false. Resetting menuDragOffset.")
                 appState.menuDragOffset = 0 // Reset without animation is fine here
            }
             // If it changed to 'open', we expect offset to be 0 anyway from the gesture end/snap back.
        }
    }

    // TabView structure (Wrapped content in NavigationStack)
    private var tabContentView: some View {
        TabView {
            NavigationStack { HomeView() }
            .tabItem { Label("Home", systemImage: "house") }.tag(0)

            NavigationStack { SearchView() } // Wrap other views too if they navigate
            .tabItem { Label("Search", systemImage: "magnifyingglass") }.tag(1)

            NavigationStack { NotificationsView() }
            .tabItem { Label("Notifications", systemImage: "bell") }.tag(2)

            NavigationStack { MessagesView() }
            .tabItem { Label("Messages", systemImage: "envelope") }.tag(3)

            NavigationStack { ProfileView() }
            .tabItem { Label("Profile", systemImage: "person") }.tag(4)
        }
    }
}
 
 
