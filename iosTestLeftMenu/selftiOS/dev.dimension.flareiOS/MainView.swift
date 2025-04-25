import SwiftUI

struct MainView: View {
    @EnvironmentObject private var appState: AppState
    @State private var selectedTab: Int = 0 // 添加选中 Tab 的状态
    private let menuWidth: CGFloat = 250

    // --- 偏移量计算 (保持不变) ---
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

    // --- Helper for overlay opacity (保持不变) ---
    private func calculateOverlayOpacity() -> Double {
        let progress = currentContentOffset / menuWidth
        return max(0, min(progress * 0.4, 0.4))
    }

    var body: some View {
        ZStack(alignment: .leading) {
            // --- Main Content Area (VStack with TabView and CustomTabBar) ---
            VStack(spacing: 0) {
                // --- Page TabView for Content ---
                TabView(selection: $selectedTab) {
                    // Keep original NavigationStack wrappers if needed
                    NavigationStack { HomeView() }.tag(0)
                    NavigationStack { SearchView() }.tag(1)
                    NavigationStack { NotificationsView() }.tag(2)
                    NavigationStack { MessagesView() }.tag(3)
                    NavigationStack { ProfileView() }.tag(4)
                }
                .tabViewStyle(.page(indexDisplayMode: .never)) // Use page style, hide indicators

                // --- Custom Bottom Tab Bar ---
                CustomTabBarView(selectedTab: $selectedTab)
                    // Inject AppState if CustomTabBar needs it, e.g., for menu toggle
                    .environmentObject(appState)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemBackground)) // Apply background to the VStack
            .offset(x: currentContentOffset)
            .zIndex(0)

            // --- Dimming Overlay (保持不变) ---
            if appState.isMenuOpen || appState.menuDragOffset > 0 {
                Color.black.opacity(calculateOverlayOpacity())
                    .contentShape(Rectangle())
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .offset(x: currentContentOffset)
                    .onTapGesture {
                        print("[MainView Overlay] Tapped. Closing menu.")
                        withAnimation(.interactiveSpring()) {
                            appState.isMenuOpen = false
                            // Offset reset will be handled by onChange
                        }
                    }
                    .zIndex(1)
                    .ignoresSafeArea()
            }

            // --- Side Menu (保持不变) ---
            MenuView()
                .frame(width: menuWidth)
                .offset(x: currentMenuOffset)
                .zIndex(2)
        }
        .contentShape(Rectangle()) // Allow ZStack to receive gestures for the new drag gesture
        // --- Animations (保持不变) ---
        .animation(.interactiveSpring(), value: appState.isMenuOpen)
        .animation(.interactiveSpring(), value: appState.menuDragOffset)
        // --- New Drag Gesture for Closing Menu ---
        .gesture(
            DragGesture(minimumDistance: 10)
                .onChanged { value in
                    // Only act when the menu is open
                    guard appState.isMenuOpen else { return }

                    let horizontalTranslation = value.translation.width
                    // Only consider leftward drag (negative translation) to close
                    if horizontalTranslation <= 0 {
                        // Update the global drag offset directly
                        appState.menuDragOffset = min(max(horizontalTranslation, -menuWidth), 0)
                        // print("[MainView DragClose] onChanged - Offset: \(appState.menuDragOffset)")
                    }
                    // Ignore rightward drags when menu is already open
                }
                .onEnded { value in
                    // Only act when the menu is open
                    guard appState.isMenuOpen else { return }

                    let currentDragOffset = appState.menuDragOffset // Use the latest value
                    let predictedEndTranslation = value.predictedEndTranslation.width
                    let quickSwipeThreshold: CGFloat = -100
                    let dragCloseThreshold = -menuWidth * 0.4 // Close if dragged more than 40% left

                    // Decide whether to close or snap back open
                    if predictedEndTranslation < quickSwipeThreshold || currentDragOffset < dragCloseThreshold {
                        print("[MainView DragClose] onEnded - Closing menu.")
                        withAnimation(.interactiveSpring()) {
                            appState.isMenuOpen = false
                            // menuDragOffset reset is handled by .onChange(of: appState.isMenuOpen)
                        }
                    } else {
                        print("[MainView DragClose] onEnded - Snapping back to open.")
                        // If not closing, we need to animate menuDragOffset back to 0
                        // since isMenuOpen remains true. The .onChange won't trigger for offset reset.
                        withAnimation(.interactiveSpring()) {
                            appState.menuDragOffset = 0
                        }
                    }
                }
        )
        // --- State Consistency (保持不变) ---
        .onChange(of: appState.isMenuOpen) { _, isOpen in
            if !isOpen && appState.menuDragOffset != 0 {
                print("[MainView onChange] isMenuOpen changed to false. Resetting menuDragOffset.")
                appState.menuDragOffset = 0 // Reset directly
            }
        }
        // Ignore bottom safe area for the entire ZStack content
        .ignoresSafeArea(edges: .bottom)
    }

    // Removed the old tabContentView computed property as its logic is now in the body
}

// Removed the duplicate placeholder struct CustomTabBarView { ... } from here.
// The correct implementation is now in CustomTabBarView.swift
 
 
