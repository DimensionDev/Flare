import AppleFontAwesome
import KotlinSharedUI
import SwiftUI

struct PlaceholderPanel: View {
    let destination: HomeTabsPresenterStateHomeTabs

    var body: some View {
        ZStack {
            Color(nsColor: .textBackgroundColor)

            VStack(spacing: 24) {
                Image(fontAwesome: destination.macOSIcon)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 48, height: 48)
                    .foregroundStyle(.secondary)

                VStack(spacing: 8) {
                    Text(destination.macOSTitle)
                        .font(.title.weight(.semibold))
                    Text(destination.macOSPlaceholder)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 420)
                }

                if destination.macOSShowsTimelineSkeleton {
                    TimelineSkeleton()
                        .frame(maxWidth: 540)
                }
            }
            .padding(32)
        }
    }
}

private struct TimelineSkeleton: View {
    var body: some View {
        VStack(spacing: 12) {
            ForEach(0..<3, id: \.self) { index in
                HStack(alignment: .top, spacing: 12) {
                    Circle()
                        .fill(.quaternary)
                        .frame(width: 36, height: 36)

                    VStack(alignment: .leading, spacing: 8) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(.quaternary)
                            .frame(width: index == 1 ? 180 : 140, height: 10)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(.quaternary)
                            .frame(height: 10)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(.quaternary)
                            .frame(width: index == 2 ? 260 : 320, height: 10)
                    }
                }
                .padding(14)
                .background(.background, in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(.quaternary)
                }
            }
        }
    }
}
