import SwiftUI
import KotlinSharedUI
import SafariServices


struct RssDetailScreen: View {
    let url: String
    var body: some View {
        if let link = URL(string: url) {
            SafariView(url: link)
                .edgesIgnoringSafeArea(.all)
        } else {
            Text("Invalid URL")
        }
    }
}


struct SafariView: UIViewControllerRepresentable {

    let url: URL

    func makeUIViewController(context: UIViewControllerRepresentableContext<SafariView>) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.entersReaderIfAvailable = true
        return SFSafariViewController(url: url, configuration: config)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: UIViewControllerRepresentableContext<SafariView>) {

    }

}
