import SwiftUI

public struct ListEmptyView: View {
    public init() {}

    public var body: some View {
        ContentUnavailableView {
            Label {
                Text("list_empty_title")
            } icon: {
                Image(systemName: "questionmark.text.page")
            }
        }
    }
}
