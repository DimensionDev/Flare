import SwiftUI
import KotlinSharedUI

struct AboutScreen: View {
    @Environment(\.openURL) private var openURL
    var body: some View {
        let versionName = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let versionCode = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
        AboutView(version: "\(versionName)(\(versionCode))", onOpenLink: { url in openURL.callAsFunction(.init(string: url)!) })
            .background(Color(.systemGroupedBackground))
            .navigationTitle("about_title")
    }
}

struct AboutView : UIViewControllerRepresentable {
    let state: ComposeUIStateProxy<KotlinUnit>
    let version: String
    
    init(
        version: String,
        onOpenLink: @escaping (String) -> Void,
    ) {
        self.version = version
        if let state = ComposeUIStateProxyCache.shared.getOrCreate(key: "about", factory: {
            .init(initialState: KotlinUnit(), onOpenLink: onOpenLink)
        }) as? ComposeUIStateProxy<KotlinUnit> {
            self.state = state
        } else {
            self.state = ComposeUIStateProxy(initialState: KotlinUnit(), onOpenLink: onOpenLink)
        }
    }
    
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.AboutScreenController(version: version, state: state)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
//        state.update(newState: version)
    }
    
    func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        ComposeUIStateProxyCache.shared.remove(key: "about")
    }
}
