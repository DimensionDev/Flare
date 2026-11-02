import SwiftUI
import FlareAppleUI
import KotlinSharedUI

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
    }
}
