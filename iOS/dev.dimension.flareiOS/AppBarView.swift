// dev.dimension.flareiOS/AppBarView.swift
import SwiftUI

struct AppBarView: View {
    // Binding to the selected tab state in the parent (HomeView)
    @Binding var selectedTopTab: Int
    // The available tabs
    let topTabs: [String]
    // Action to perform when avatar is tapped
    let onAvatarTap: () -> Void

    var body: some View {
        HStack(spacing: 15) { // Added spacing
            // Avatar Button
            Button(action: onAvatarTap) {
                Image(systemName: "person.crop.circle")
                    .imageScale(.large)
            }
            .padding(.leading, 8) // Add padding

            // Scrollable Top Tabs
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 20) {
                    ForEach(topTabs.indices, id: \.self) { index in
                        Button(topTabs[index]) {
                            // Update the binding when a tab is tapped
                            selectedTopTab = index
                        }
                        .padding(.vertical, 8)
                        .foregroundStyle(selectedTopTab == index ? .primary : .secondary)
                        // Simple underline for selected tab
                        .overlay(alignment: .bottom) {
                            if selectedTopTab == index {
                                Rectangle().frame(height: 2).foregroundStyle(.blue)
                            }
                        }
                        // Add animation for selection change if needed later
                    }
                }
                // Removed padding(.horizontal) here, let ScrollView handle edges
            }

            Spacer().frame(width: 44).padding(.trailing, 8) // Keep the spacing consistent if needed, or remove entirely

        }
        .frame(height: 44) // Standard app bar height
        .background(Material.bar) // Use a Material background for a more standard app bar feel
        // No Divider needed here, will be added in HomeView
    }
} 