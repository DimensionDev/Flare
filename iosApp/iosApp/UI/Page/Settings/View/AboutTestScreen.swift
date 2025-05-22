import sharedUI
import SwiftUI

struct AboutTestScreen: View {
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        NavigationStack {
            ZStack {
                Text("AboutTestScreen")
                    .foregroundColor(theme.labelColor)
            }
            .navigationTitle("About")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.primaryBackgroundColor) // 设置 ZStack 的背景色
        }
        .background(theme.primaryBackgroundColor)
        .scrollContentBackground(.hidden)
        .listRowBackground(theme.primaryBackgroundColor)
    }
}
