
import Kingfisher
import SwiftUI

struct WaterfallItemView: View {
    let item: WaterfallItem

    let onTap: (ClickAction) -> Void

    @Environment(FlareTheme.self) private var theme

    @Environment(\.appSettings) private var appSettings

    private var imageSize: CGSize {
        let screenWidth = UIScreen.main.bounds.width
        let padding: CGFloat = 32
        let spacing: CGFloat = 4
        let columnWidth = (screenWidth - padding - spacing) / 2

        let imageHeight = columnWidth / item.aspectRatio
        return CGSize(width: columnWidth, height: imageHeight)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            imageContentView

            if item.shouldShowText {
                textContentView
            }
        }
        .background(backgroundView)
        .contentShape(Rectangle())
        .onTapGesture {
            // 默认点击行为：如果有文字内容则显示详情，否则显示媒体预览
            if item.shouldShowText {
                onTap(item.contentClickAction)
            } else {
                onTap(item.imageClickAction)
            }
        }
        .onAppear {
            FlareLog.debug("WaterfallItemView onAppear: id=\(item.id), aspectRatio=\(item.aspectRatio), imageSize=\(imageSize), previewURL=\(item.previewURL?.absoluteString ?? "nil")")

            if let url = item.previewURL {
                FlareLog.debug("WaterfallItemView checking URL: \(url.absoluteString)")
            } else {
                FlareLog.warning("WaterfallItemView missing previewURL for item: \(item.id)")
            }
        }
    }

    private var imageContentView: some View {
        ZStack {
            KFImage(item.previewURL)
                .flareTimelineMedia(size: CGSize(width: imageSize.width, height: imageSize.height), priority: 0.6)
                .placeholder {
                    imagePlaceholder
                }
                .onFailure { error in
                    FlareLog.error("WaterfallItemView 图片加载失败: \(item.id), URL: \(item.previewURL?.absoluteString ?? "nil"), Error: \(error)")
                }
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: imageSize.width, height: imageSize.height)
                .clipped()

            if item.isVideo {
                videoPlayIcon
            }

            if shouldShowSensitiveMask {
                sensitiveMaskView
            }
        }
        .frame(width: imageSize.width, height: imageSize.height)
        .contentShape(Rectangle())
        .cornerRadius(8)
    }

    private var imagePlaceholder: some View {
        ZStack {
            Rectangle()
                .fill(Color.gray.opacity(0.3))

            VStack(spacing: 4) {
                ProgressView()
                    .scaleEffect(0.8)
                    .tint(.blue)
                Text("Loading...")
                    .font(.caption2)
                    .foregroundColor(.black)
            }
        }
        .frame(width: imageSize.width, height: imageSize.height)
        .clipped()
    }

    private var videoPlayIcon: some View {
        Image(systemName: "play.circle.fill")
            .font(.system(size: 20))
            .foregroundColor(.white)
            .shadow(color: .black.opacity(0.5), radius: 2, x: 0, y: 1)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.bottom, 8)
            .padding(.trailing, 8)
    }

    private var sensitiveMaskView: some View {
        Rectangle()
            .fill(.ultraThinMaterial)
            .overlay {
                VStack(spacing: 8) {
                    Image(systemName: "eye.slash")
                        .font(.title2)
                    Text("敏感内容")
                        .font(.caption)
                }
                .foregroundColor(.secondary)
            }
    }

    @ViewBuilder
    private var textContentView: some View {
        VStack(alignment: .leading, spacing: 4) {
            userInfoView

            if !item.previewText.isEmpty {
                contentTextView
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
    }

    private var userInfoView: some View {
        HStack(spacing: 6) {
            KFImage(URL(string: item.sourceTimelineItem.user?.avatar ?? ""))
                .flareTimelineAvatar(size: CGSize(width: 16, height: 16))
                .placeholder {
                    Circle()
                        .fill(theme.secondaryBackgroundColor)
                }
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: 16, height: 16)
                .clipShape(Circle())

            Text(item.sourceTimelineItem.user?.name.raw ?? "")
                .font(.caption)
                .foregroundColor(theme.labelColor.opacity(0.7))
                .lineLimit(1)

            Spacer()

            Text(formatTimestamp(item.sourceTimelineItem.timestamp))
                .font(.caption2)
                .foregroundColor(theme.labelColor.opacity(0.5))
        }
    }

    private var contentTextView: some View {
        Text(item.previewText)
            .font(.caption)
            .foregroundColor(theme.labelColor)
            .lineLimit(3)
            .multilineTextAlignment(.leading)
    }

    @ViewBuilder
    private var backgroundView: some View {
        if item.shouldShowText {
            RoundedRectangle(cornerRadius: 12)
                .fill(theme.primaryBackgroundColor)
                .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 1)
        } else {
            Color.clear
        }
    }

    private var shouldShowSensitiveMask: Bool {
        item.sourceTimelineItem.sensitive &&
            !appSettings.appearanceSettings.showSensitiveContent
    }

    private func formatTimestamp(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
