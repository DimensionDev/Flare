import SwiftUI
import UIKit

struct AppIconSettingsScreen: View {
    @State private var currentIconName = UIApplication.shared.alternateIconName
    @State private var pendingIconID: String?
    @State private var errorMessage: String?

    private let columns = [
        GridItem(.adaptive(minimum: 88, maximum: 120), spacing: 20)
    ]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 20) {
                ForEach(AppIconOption.all) { option in
                    Button {
                        setIcon(option)
                    } label: {
                        AppIconGridItem(
                            option: option,
                            isSelected: currentIconName == option.alternateIconName,
                            isPending: pendingIconID == option.id
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(option.title)
                    .accessibilityAddTraits(currentIconName == option.alternateIconName ? [.isSelected] : [])
                    .disabled(
                        !UIApplication.shared.supportsAlternateIcons ||
                        pendingIconID != nil
                    )
                }
            }
            .padding()

            if !UIApplication.shared.supportsAlternateIcons {
                Text("This device does not support alternate app icons.")
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
        }
        .navigationTitle("App Icon")
    }

    private func setIcon(_ option: AppIconOption) {
        guard UIApplication.shared.supportsAlternateIcons else {
            return
        }
        guard currentIconName != option.alternateIconName else {
            return
        }

        errorMessage = nil
        pendingIconID = option.id
        UIApplication.shared.setAlternateIconName(option.alternateIconName) { error in
            DispatchQueue.main.async {
                pendingIconID = nil
                if let error {
                    errorMessage = error.localizedDescription
                } else {
                    currentIconName = UIApplication.shared.alternateIconName
                }
            }
        }
    }
}

private struct AppIconGridItem: View {
    let option: AppIconOption
    let isSelected: Bool
    let isPending: Bool

    var body: some View {
        GeometryReader { proxy in
            let cornerRadius = proxy.size.width * 0.218
            let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)

            ZStack(alignment: .topTrailing) {
                Image(option.previewImageName)
                    .resizable()
                    .aspectRatio(1, contentMode: .fit)
                    .clipShape(shape)
                    .overlay {
                        shape.stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 3)
                    }
                    .shadow(color: .black.opacity(0.08), radius: 8, y: 4)

                if isPending {
                    ProgressView()
                        .padding(8)
                        .background(.regularMaterial, in: Circle())
                } else if isSelected {
                    Image(.faCheck)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 24, height: 24)
                        .background(Color.accentColor, in: Circle())
                        .padding(6)
                }
            }
            .contentShape(shape)
        }
        .aspectRatio(1, contentMode: .fit)
    }
}
