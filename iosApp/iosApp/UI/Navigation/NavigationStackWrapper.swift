import SwiftUI


struct NavigationStackWrapper<Content: View>: View {
    @Environment(\.appSettings) private var appSettings
    @Binding var navigationPath: NavigationPath  
    var content: () -> Content
    
    @State private var swipeGesture: UIPanGestureRecognizer = {
        let gesture = UIPanGestureRecognizer()
        gesture.name = UUID().uuidString
        gesture.isEnabled = false
        return gesture
    }()
    init(path: Binding<NavigationPath>, @ViewBuilder content: @escaping () -> Content) { // Modified init
        self._navigationPath = path 
        self.content = content
    }
    
    var body: some View {
        NavigationStack(path: $navigationPath) {
            content()
                .background {
                    AttachGestureView(gesture: $swipeGesture, navigationDepth: navigationPath.count) // Use path count
                }
        }
        .enabledFullSwipePop(appSettings.appearanceSettings.enableFullSwipePop)  
        .environment(\.popGestureID, swipeGesture.name)
        .onReceive(NotificationCenter.default.publisher(for: .init(swipeGesture.name ?? "")), perform: { info in
            if let userInfo = info.userInfo, let status = userInfo["status"] as? Bool {
                swipeGesture.isEnabled = status
            }
        })
    }
}

fileprivate struct PopNotificationID: EnvironmentKey {
    static var defaultValue: String?
}

fileprivate extension EnvironmentValues {
    var popGestureID: String? {
        get {
            self[PopNotificationID.self]
        }
        
        set {
            self[PopNotificationID.self] = newValue
        }
    }
}

extension View {
    @ViewBuilder
    func enabledFullSwipePop(_ isEnabled: Bool) -> some View {
        self
            .modifier(FullSwipeModifier(isEnabled: isEnabled))
    }
}

fileprivate struct FullSwipeModifier: ViewModifier {
    var isEnabled: Bool
    @Environment(\.popGestureID) private var gestureID
    func body(content: Content) -> some View {
        content
            .onChange(of: isEnabled, initial: true) { _, new in
                guard let gestureID = gestureID else { return }
                NotificationCenter.default.post(name: .init(gestureID), object: nil, userInfo: [
                    "status": new
                ])
            }
            .onDisappear {
                guard let gestureID = gestureID else { return }
                NotificationCenter.default.post(name: .init(gestureID), object: nil, userInfo: [
                    "status": false
                ])
            }
    }
}

fileprivate struct AttachGestureView: UIViewRepresentable {
    @Binding var gesture: UIPanGestureRecognizer
    let navigationDepth: Int
    
    func makeUIView(context: Context) -> some UIView {
        return UIView()
    }
    
    func updateUIView(_ uiView: UIViewType, context: Context) {
        DispatchQueue.main.async {
            if let parentViewController = uiView.parentViewController {
                if let navigationController = parentViewController.navigationController {
                    // Check if the navigation stack has more than one view controller
                    if self.navigationDepth > 0 {
                        if let _ = navigationController.view.gestureRecognizers?.first(where: { $0.name == self.gesture.name }) {
                            print("Already attached")
                        } else {
                            navigationController.addFullSwipeGesture(self.gesture)
                            print("Attached")
                        }
                    } else {
                        // Remove the gesture if the navigation stack count is below the threshold
                        if let existingGesture = navigationController.view.gestureRecognizers?.first(where: { $0.name == self.gesture.name }) {
                            navigationController.view.removeGestureRecognizer(existingGesture)
                            print("Detached")
                        }
                    }
                }
            }
        }
    }
}

fileprivate extension UINavigationController {
    func addFullSwipeGesture(_ gesture: UIPanGestureRecognizer) {
        guard let gestureSelector = interactivePopGestureRecognizer?.value(forKey: "targets") else { return }
        
        gesture.setValue(gestureSelector, forKey: "targets")
        view.addGestureRecognizer(gesture)
    }
}

fileprivate extension UIView {
    var parentViewController: UIViewController? {
        sequence(first: self) {
            $0.next
        }.first(where: { $0 is UIViewController }) as? UIViewController
    }
}
