import SwiftUI
import KotlinSharedUI
import MarkdownUI

struct RichText: View {
    let text: UiRichText
    
    var body: some View {
        Markdown(text.markdown)
    }
}
