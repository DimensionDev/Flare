import SwiftUI

struct SensitiveContentButton: View {
    let hideSensitive: Bool
    let action: () -> Void
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ZStack {
            Button(action: {
                FlareHapticManager.shared.buttonPress()
                withAnimation {
                    action()
                }
            }, label: {
                if hideSensitive {
                    Image(systemName: "eye")
                        .foregroundColor(theme.tintColor)
                } else {
                    Image(systemName: "eye.slash")
                        .foregroundColor(theme.tintColor)
                }
            })
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(.ultraThinMaterial)
            .cornerRadius(8)
            .buttonStyle(.plain)
            .padding(.top, 16)
            .padding(.leading, 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}
