import SwiftUI

struct ReleaseLogBannerView: View {
    @ObservedObject private var releaseLogManager = ReleaseLogManager.shared
    @Environment(FlareTheme.self) private var theme
    @State private var showReleaseLogSheet = false

    let onDismiss: () -> Void

    var body: some View {
        Button(action: {
            showReleaseLogSheet = true
        }) {
            HStack(spacing: 12) {
                Image(systemName: "sparkles")
                    .foregroundColor(theme.tintColor)
                    .font(.title2)

                VStack(alignment: .leading, spacing: 4) {
                    Text(releaseLogManager.getBannerText())
                        .font(.body)
                        .foregroundColor(theme.labelColor)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundColor(theme.labelColor.opacity(0.6))
                    .font(.caption)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .buttonStyle(PlainButtonStyle())
        .background(theme.primaryBackgroundColor)
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .sheet(isPresented: $showReleaseLogSheet, onDismiss: {
            if let currentVersion = releaseLogManager.getCurrentAppVersion() {
                releaseLogManager.markVersionLogAsShown(version: currentVersion)
                onDismiss()
            }
        }) {
            NavigationView {
                ReleaseLogScreen()
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Close") {
                                showReleaseLogSheet = false
                            }
                            .foregroundColor(theme.tintColor)
                        }
                    }
            }
        }
        .transition(.asymmetric(
            insertion: .opacity.combined(with: .move(edge: .top)),
            removal: .opacity.combined(with: .move(edge: .top))
        ))
        .animation(.easeInOut(duration: 0.3), value: showReleaseLogSheet)
    }
}
