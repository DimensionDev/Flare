import SwiftUI
import KotlinSharedUI

struct StatusMediaView: View {
    let data: [any UiMedia]
    
    var body: some View {
        AdaptiveMosaic(data, spacing: 4, singleMode: .force16x9) { item in
            MediaView(data: item)
                .clipped()
        }
        .clipShape(.rect(cornerRadius: 16))
    }
}
