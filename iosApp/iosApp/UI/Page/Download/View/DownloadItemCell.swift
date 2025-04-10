import Kingfisher
import SwiftUI

struct DownloadItemCell: View {
    let item: DownloadItem
    let onTapAction: () -> Void
    let onShareAction: (DownloadItem) -> Void

    private enum Design {
        static let thumbnailSize: CGFloat = 56
        static let cornerRadius: CGFloat = 12
        static let cellPadding: CGFloat = 16
        static let contentSpacing: CGFloat = 12
        static let progressHeight: CGFloat = 2
        static let iconSize: CGFloat = 20

        static let titleFont = Font.system(size: 16, weight: .medium)
        static let subtitleFont = Font.system(size: 13, weight: .regular)
        static let captionFont = Font.system(size: 12, weight: .regular)

        static let progressTint = Color.blue
        static let pausedTint = Color.gray.opacity(0.5)
    }

    var body: some View {
        HStack(spacing: Design.contentSpacing) {
            Button(action: onTapAction) {
                thumbnailView
                    .frame(width: Design.thumbnailSize, height: Design.thumbnailSize)
                    .cornerRadius(Design.cornerRadius)
                    .overlay(
                        RoundedRectangle(cornerRadius: Design.cornerRadius)
                            .stroke(Color.gray.opacity(0.1), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)

            Button(action: onTapAction) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.fileName)
                        .font(Design.titleFont)
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    downloadInfoView
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            Button {
                onShareAction(item)
            } label: {
                Image(systemName: statusIcon)
                    .font(.system(size: Design.iconSize, weight: .medium))
                    .foregroundColor(statusColor)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .frame(width: 44)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, Design.cellPadding)
        .background(Color(.systemBackground))
    }

    private var thumbnailView: some View {
        KFImage(URL(string: item.imageUrl ?? ""))
            .placeholder {
                ZStack {
                    Color(.systemGray6)
                    Image(systemName: typeIcon)
                        .font(.system(size: Design.iconSize, weight: .medium))
                        .foregroundColor(.gray)
                }
            }
            .resizable()
            .aspectRatio(contentMode: .fill)
    }

    private var typeIcon: String {
        switch item.downItemType {
        case .video: "play.circle.fill"
        case .gif: "gift.circle.fill"
        case .image: "photo.circle.fill"
        case .unknown: "doc.circle.fill"
        case .audio: "audio.circle.fill"
        }
    }

    private var downloadInfoView: some View {
        VStack(alignment: .leading, spacing: 6) {
            statusView

            HStack(spacing: 8) {
                if item.totalSize > 0, item.status == .downloading || item.status == .paused {
                    Text("\(item.formattedDownloadedSize) / \(item.formattedTotalSize)")
                } else if item.status == .downloaded {
                    Text("\(item.formattedTotalSize) / \(item.formattedTotalSize)")
                } else if item.totalSize > 0 {
                    Text(item.formattedTotalSize)
                } else {
                    Text("-- MB")
                }

                if let duration = item.formattedDuration {
                    Text("•")
                        .foregroundColor(.secondary.opacity(0.5))
                    Text(duration)
                }
            }
            .font(Design.captionFont)
            .foregroundColor(.secondary)
        }
    }

    private var statusView: some View {
        Group {
            switch item.status {
            case .initial:
                Text("waiting")
                    .font(Design.subtitleFont)
                    .foregroundColor(.secondary)
            case .downloading:
                VStack(alignment: .leading, spacing: 4) {
                    // 进度条
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Rectangle()
                                .fill(Color(.systemGray5))
                            Rectangle()
                                .fill(Design.progressTint)
                                .frame(width: geometry.size.width * item.progress)
                        }
                    }
                    .frame(height: Design.progressHeight)
                    .cornerRadius(Design.progressHeight / 2)

                    // 进度
                    Text(item.statusDescription)
                        .font(Design.captionFont)
                        .foregroundColor(.secondary)
                }
            case .downloaded:
                Text("downloaded")
                    .font(Design.subtitleFont)
                    .foregroundColor(.green)
            case .removed:
                Text("removed")
                    .font(Design.subtitleFont)
                    .foregroundColor(.red)
            case .failed:
                Text(item.statusDescription)
                    .font(Design.subtitleFont)
                    .foregroundColor(.red)
            case .paused:
                VStack(alignment: .leading, spacing: 4) {
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Rectangle()
                                .fill(Color(.systemGray5))
                            Rectangle()
                                .fill(Design.pausedTint)
                                .frame(width: geometry.size.width * item.progress)
                        }
                    }
                    .frame(height: Design.progressHeight)
                    .cornerRadius(Design.progressHeight / 2)

                    Text("paused - \(Int(item.progress * 100))%")
                        .font(Design.captionFont)
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    private var statusIcon: String {
        switch item.status {
        case .initial:
            "arrow.down"
        case .downloading:
            "pause"
        case .downloaded:
            "square.and.arrow.up"
        case .removed:
            "xmark"
        case .failed:
            "exclamationmark.triangle"
        case .paused:
            "play.fill"
        }
    }

    private var statusColor: Color {
        switch item.status {
        case .initial:
            .blue
        case .downloading:
            .blue
        case .downloaded:
            .blue
        case .removed:
            .red
        case .failed:
            .orange
        case .paused:
            .blue
        }
    }
}
