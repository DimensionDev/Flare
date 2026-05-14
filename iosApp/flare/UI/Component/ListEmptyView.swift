import SwiftUI

struct ListEmptyView: View {
    var body: some View {
        ContentUnavailableView {
            Label {
                Text("list_empty_title")
            } icon: {
                Image(systemName: "questionmark.text.page")
            }
        }
    }
}
