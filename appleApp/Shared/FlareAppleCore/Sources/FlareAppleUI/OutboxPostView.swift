import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

public struct OutboxPostView: View {
    private let post: UiOutboxPost
    private let onRetry: () -> Void
    private let onEdit: () -> Void
    private let onDelete: () -> Void

    public init(
        post: UiOutboxPost,
        onRetry: @escaping () -> Void,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) {
        self.post = post
        self.onRetry = onRetry
        self.onEdit = onEdit
        self.onDelete = onDelete
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header

            if let spoilerText = post.data.spoilerText?.outboxNonEmpty {
                Text(spoilerText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            if let content = post.data.content.outboxNonEmpty {
                Text(content)
                    .font(.body)
                    .lineLimit(6)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            if !post.medias.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(Array(post.medias.prefix(4).enumerated()), id: \.offset) { _, media in
                            OutboxMediaThumbnail(media: media)
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }

            ProgressView(
                value: Double(post.progressCurrent),
                total: Double(max(post.progressMax, 1))
            )
            Text(
                FlareAppleUILocalization.string(
                    "outbox_progress",
                    fallback: "Step %d of %d",
                    arguments: [post.progressCurrent, post.progressMax]
                )
            )
            .font(.caption)
            .foregroundStyle(.secondary)

            if post.targets.count > 1 {
                VStack(spacing: 6) {
                    ForEach(Array(post.targets.enumerated()), id: \.offset) { _, target in
                        OutboxTargetRow(target: target)
                    }
                }
            }

            if post.status == .failed {
                if let message = post.targets.compactMap(\.errorMessage).first(where: { !$0.isEmpty }) {
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .lineLimit(2)
                }
                HStack {
                    Spacer()
                    Button(action: onEdit) {
                        Text(FlareAppleUILocalization.string("edit", fallback: "Edit"))
                    }
                    Button(role: .destructive, action: onDelete) {
                        Text(FlareAppleUILocalization.string("delete", fallback: "Delete"))
                    }
                    Button(action: onRetry) {
                        Text(FlareAppleUILocalization.string("action_retry", fallback: "Retry"))
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.flareSecondarySystemGroupedBackground)
        .opacity(post.status == .failed ? 1 : 0.62)
        .disabled(post.status != .failed)
        .accessibilityElement(children: .contain)
        .accessibilityValue(Text(post.status.localizedTitle))
    }

    private var header: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                ForEach(Array(post.targets.prefix(4).enumerated()), id: \.offset) { _, target in
                    if let avatar = target.avatar {
                        NetworkImage(data: avatar.url, customHeader: avatar.customHeaders)
                            .frame(width: 24, height: 24)
                            .clipShape(Circle())
                    } else {
                        Image(systemName: "person.crop.circle.fill")
                            .resizable()
                            .scaledToFit()
                            .foregroundStyle(.secondary)
                            .frame(width: 24, height: 24)
                    }
                }
                if post.targets.count == 1, let target = post.targets.first {
                    Text(target.account.accountKey.description())
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            DateTimeText(data: post.updatedAt)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Text(post.status.localizedTitle)
                .font(.caption.weight(.semibold))
                .foregroundStyle(post.status.tint)
                .lineLimit(1)
        }
    }
}

private struct OutboxTargetRow: View {
    let target: UiOutboxTarget

    var body: some View {
        HStack(spacing: 8) {
            if let avatar = target.avatar {
                NetworkImage(data: avatar.url, customHeader: avatar.customHeaders)
                    .frame(width: 20, height: 20)
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .foregroundStyle(.secondary)
                    .frame(width: 20, height: 20)
            }
            Text(target.account.accountKey.description())
                .font(.caption)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(target.status.localizedTitle)
                .font(.caption.weight(.medium))
                .foregroundStyle(target.status.tint)
            Text("\(target.progressCurrent)/\(target.progressMax)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
    }
}

private struct OutboxMediaThumbnail: View {
    let media: UiDraftMedia

    var body: some View {
        Group {
            switch media.type {
            case .image:
                platformImage
            case .video:
                placeholder(icon: "video")
            case .other:
                placeholder(icon: "doc")
            }
        }
        .frame(width: 64, height: 64)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    @ViewBuilder
    private var platformImage: some View {
        #if os(iOS)
        if let image = UIImage(contentsOfFile: media.cachePath) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
        } else {
            placeholder(icon: "photo")
        }
        #elseif os(macOS)
        if let image = NSImage(contentsOfFile: media.cachePath) {
            Image(nsImage: image)
                .resizable()
                .scaledToFill()
        } else {
            placeholder(icon: "photo")
        }
        #else
        placeholder(icon: "photo")
        #endif
    }

    private func placeholder(icon: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(.quaternary)
            Image(systemName: icon)
                .foregroundStyle(.secondary)
        }
    }
}

private extension UiOutboxStatus {
    var localizedTitle: String {
        switch self {
        case .sending:
            FlareAppleUILocalization.string("outbox_status_sending", fallback: "Sending")
        case .failed:
            FlareAppleUILocalization.string("outbox_status_failed", fallback: "Failed")
        case .sent:
            FlareAppleUILocalization.string("outbox_status_sent", fallback: "Sent")
        }
    }

    var tint: Color {
        switch self {
        case .sending:
            .accentColor
        case .failed:
            .red
        case .sent:
            .green
        }
    }
}

private extension String {
    var outboxNonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
