import SwiftUI
import KotlinSharedUI

struct StatusMediaView: View {
    let data: [any UiMedia]
    
    var body: some View {
        AdaptiveMosaic(data, spacing: 4) { item in
            MediaView(data: item)
                .clipped()
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
