import SwiftUI
import FlareAppleUI
import KotlinSharedUI
import SwiftUIBackports

struct DeepLinkAccountPicker: View {
    let originalUrl: String
    let data: [MicroBlogKey : Route]
    let onNavigate: (Route) -> Void
    
    var body: some View {
        DeepLinkAccountPickerView(
            originalUrl: originalUrl,
            data: data,
            onNavigate: onNavigate
        )
        .backport
        .navigationSubtitle("deep_link_account_picker_subtitle")
    }
}
