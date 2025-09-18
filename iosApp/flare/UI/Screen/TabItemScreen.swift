import SwiftUI
import KotlinSharedUI

struct TabItemScreen: View {
    let tabItem: TabItem
    let onNavigate: (Route) -> Void
    var body: some View {
        tabItem.view(onNavigate: onNavigate)
    }
}
