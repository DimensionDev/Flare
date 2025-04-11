import SwiftUI
import SwiftUICore

extension View {
    func confirmationDialog(
        title: String,
        message: String,
        isPresented: Binding<Bool>,
        action: @escaping () -> Void
    ) -> some View {
        confirmationDialog(
            title,
            isPresented: isPresented,
            titleVisibility: .visible,
            actions: {
                Button("OK", role: .destructive, action: action)
                Button("Cancel", role: .cancel) {}
            },
            message: {
                Text(message)
            }
        )
    }
}
