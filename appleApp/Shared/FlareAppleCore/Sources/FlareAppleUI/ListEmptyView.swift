import FlareAppleCore
import SwiftUI

public struct ListEmptyView: View {
    private let title: LocalizedStringKey
    private let icon: Image

    public init() {
        title = "list_empty_title"
        icon = Image(systemName: "questionmark.text.page")
    }

    public init(_ title: LocalizedStringKey, icon: FontAwesomeIcon) {
        self.title = title
        self.icon = Image(fontAwesome: icon)
    }

    public var body: some View {
        ContentUnavailableView {
            Label {
                Text(title, bundle: FlareAppleUILocalization.bundle)
            } icon: {
                icon
            }
        }
    }
}
