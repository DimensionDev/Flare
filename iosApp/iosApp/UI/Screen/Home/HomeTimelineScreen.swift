import SwiftUI
import shared

struct ComposeTimelineViewController: UIViewControllerRepresentable {
    let presenter: TimelinePresenter
    let accountType: AccountType
    let darkMode: Bool
    let onOpenLink: (String) -> Void
    func makeUIViewController(context: Context) -> UIViewController {
        return TimelineViewController(presenter: presenter, accountType: accountType, darkMode: darkMode, onOpenLink: onOpenLink)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct TimelineScreen: View {
    @Environment(\.openURL) private var openURL
    let presenter: TimelinePresenter
    let accountType: AccountType
    @Environment(\.colorScheme) var colorScheme: ColorScheme

    var body: some View {
        ComposeTimelineViewController(presenter: presenter, accountType: accountType, darkMode: colorScheme == .dark, onOpenLink: { openURL(.init(string: $0)!) })
    }
}

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    @State private var presenter: HomeTimelinePresenter
    private let accountType: AccountType
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType
    }
    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            TimelineScreen(presenter: presenter, accountType: accountType)
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #else
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        Task {
                            try? await state.refresh()
                        }
                    }, label: {
                        Image(systemName: "arrow.clockwise.circle")
                    })
                }
            }
            #endif
        }
        .navigationTitle("home_timeline_title")
    }
}
