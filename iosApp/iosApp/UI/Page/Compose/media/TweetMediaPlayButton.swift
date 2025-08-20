import SwiftUI

public struct TweetMediaPlayButton: View {
    let duration: String?
    let tapAction: (() -> Void)?

    init(duration: String?, tapAction: (() -> Void)? = nil) {
        self.duration = duration
        self.tapAction = tapAction
    }

    public var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "play.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 12, height: 12)

            if let duration {
                Text(duration)
                    .font(.system(size: 12, weight: .medium))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background {
            Color.black.opacity(0.5)
        }
        .clipShape(RoundedRectangle(cornerRadius: 4))
        .foregroundColor(.white)
        .onTapGesture {
            FlareHapticManager.shared.buttonPress()
            tapAction?()
        }
    }
}
