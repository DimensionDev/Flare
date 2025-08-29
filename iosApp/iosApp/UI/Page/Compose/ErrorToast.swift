import SwiftUI


class ErrorToastManager: ObservableObject {
    static let shared = ErrorToastManager()

    @Published var isShowing = false
    @Published var message = ""

    private init() {}

    func show(message: String) {
        DispatchQueue.main.async {
            self.message = message
            self.isShowing = true

            // 3秒后自动隐藏
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                self.isShowing = false
            }
        }
    }

    func hide() {
        DispatchQueue.main.async {
            self.isShowing = false
        }
    }
}



struct ErrorToast: View {
    let message: String
    @Binding var isShowing: Bool

    var body: some View {
        VStack {
            Spacer()

            if isShowing {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.white)
                        .font(.system(size: 16))

                    Text(message)
                        .foregroundColor(.white)
                        .font(.system(size: 14, weight: .medium))
                        .multilineTextAlignment(.leading)
                        .lineLimit(3)

                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.red.opacity(0.9))
                )
                .padding(.horizontal, 20)
                .padding(.bottom, 100)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.easeInOut(duration: 0.3), value: isShowing)
            }
        }
        .allowsHitTesting(false)
    }
}



extension View {
    func errorToast() -> some View {
        overlay(
            ErrorToastOverlay()
        )
        .environmentObject(ErrorToastManager.shared)
    }
}


struct ErrorToastOverlay: View {
    @ObservedObject private var toastManager = ErrorToastManager.shared

    var body: some View {
        ErrorToast(
            message: toastManager.message,
            isShowing: $toastManager.isShowing
        )
    }
}



func showErrorToast(message: String) {
    ErrorToastManager.shared.show(message: message)
}



