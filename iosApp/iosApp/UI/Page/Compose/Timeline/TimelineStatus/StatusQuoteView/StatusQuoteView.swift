import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
 
import SwiftUI
import UIKit

struct StatusQuoteView: View {
    let quotes: [UiTimelineItemContentStatus]
    let onMediaClick: (Int, UiMedia) -> Void

    var body: some View {
        Spacer().frame(height: 10)

        VStack {
            ForEach(0 ..< quotes.count, id: \.self) { index in
                let quote = quotes[index]
                QuotedStatus(data: quote, onMediaClick: onMediaClick)
                    .foregroundColor(.gray)

                if index != quotes.count - 1 {
                    Divider()
                }
            }
        }
        .padding(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
        )
        .cornerRadius(8)
    }
}
