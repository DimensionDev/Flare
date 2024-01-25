import Foundation
import PhotosUI
import SwiftUI
import shared

@Observable
class ComposeViewModel: MoleculeViewModelProto {
    typealias Model = ComposeState
    typealias Presenter = ComposePresenter
    let presenter: ComposePresenter
    var model: ComposeState
    var text = ""
    var contentWarning = ""
    var enableCW = false
    var pollViewModel = PollViewModel()
    var mediaViewModel = MediaViewModel()
    var status: ComposeStatus?
    var showEmoji = false
    init(status: ComposeStatus?) {
        self.status = status
        presenter = ComposePresenter(status: status)
        model = presenter.models.value
    }
    func showEmojiPanel() {
        showEmoji = true
    }
    func toggleCW() {
        enableCW = !enableCW
    }
    func togglePoll() {
        if pollViewModel.enabled {
            pollViewModel = PollViewModel()
        } else {
            pollViewModel.enabled = true
        }
    }
    func addEmoji(emoji: UiEmoji) {
        text += " :" + emoji.shortcode + ": "
        showEmoji = false
    }
    func send() {
        Task {
            if case .success(let account) = onEnum(of: self.model.account) {
                let data = switch onEnum(of: account.data) {
                case .bluesky(let bluesky):
                    BlueskyComposeData(
                        account: bluesky,
                        content: text,
                        inReplyToID: getReplyId(),
                        quoteId: getQuoteId(),
                        language: ["en"],
                        medias: getMedia()
                    ) as ComposeData_
                case .mastodon(let mastodon):
                    MastodonComposeData(
                        account: mastodon,
                        content: text,
                        visibility: getMastodonVisibility(),
                        inReplyToID: getReplyId(),
                        medias: getMedia(),
                        sensitive: mediaViewModel.sensitive,
                        spoilerText: contentWarning,
                        poll: getMastodonPoll()
                    ) as ComposeData_
                case .misskey(let misskey):
                    MisskeyComposeData(
                        account: misskey,
                        content: text,
                        visibility: getMisskeyVisibility(),
                        inReplyToID: getReplyId(),
                        renoteId: getQuoteId(),
                        medias: getMedia(),
                        sensitive: mediaViewModel.sensitive,
                        spoilerText: contentWarning,
                        poll: getMisskeyPoll(),
                        localOnly: false
                    ) as ComposeData_
                case .xQT(let xqt):
                    XQTComposeData(
                        account: xqt,
                        content: text,
                        inReplyToID: getReplyId(),
                        quoteId: getQuoteId(),
                        quoteUsername: getXQTUserName(),
                        medias: getMedia(),
                        poll: getXQTPoll(),
                        sensitive: mediaViewModel.sensitive
                    )
                }
                model.send(data: data)
            }
        }
    }
    private func getMedia() -> [FileItem] {
        return mediaViewModel.items.map { item in
            FileItem(name: item.item.itemIdentifier, data: KotlinByteArray.from(data: item.data!))
        }
    }
    private func getQuoteId() -> String? {
        return if let data = status, case .quote(let quote) = onEnum(of: data) {
            quote.statusKey.id
        } else {
            nil
        }
    }
    private func getReplyId() -> String? {
        return if let data = status, case .reply(let reply) = onEnum(of: data) {
            reply.statusKey.id
        } else {
            nil
        }
    }
    private func getMastodonVisibility() -> UiStatus.Mastodon.MastodonVisibility {
        return if case .success(let data) = onEnum(of: model.visibilityState),
                  let state = data.data as? MastodonVisibilityState {
            state.visibility
        } else {
            UiStatus.Mastodon.MastodonVisibility.public
        }
    }
    private func getMastodonPoll() -> MastodonComposeData.Poll? {
        if pollViewModel.enabled {
            MastodonComposeData.Poll(
                options: pollViewModel.choices.map { item in
                    item.text
                },
                expiresIn: pollViewModel.expired.inWholeMilliseconds,
                multiple: pollViewModel.pollType == ComposePollType.multiple
            )
        } else {
            nil
        }
    }
    private func getMisskeyVisibility() -> UiStatus.Misskey.MisskeyVisibility {
        return if case .success(let data) = onEnum(of: model.visibilityState),
                  let state = data.data as? MisskeyVisibilityState {
            state.visibility
        } else {
            UiStatus.Misskey.MisskeyVisibility.public
        }
    }
    private func getMisskeyPoll() -> MisskeyComposeData.Poll? {
        if pollViewModel.enabled {
            MisskeyComposeData.Poll(
                options: pollViewModel.choices.map { item in
                    item.text
                },
                expiredAfter: pollViewModel.expired.inWholeMilliseconds,
                multiple: pollViewModel.pollType == ComposePollType.multiple
            )
        } else {
            nil
        }
    }
    private func getXQTPoll() -> XQTComposeData.Poll? {
        if pollViewModel.enabled {
            XQTComposeData.Poll(
                options: pollViewModel.choices.map { item in
                    item.text
                },
                expiredAfter: pollViewModel.expired.inWholeMilliseconds,
                multiple: pollViewModel.pollType == ComposePollType.multiple
            )
        } else {
            nil
        }
    }
    private func getXQTUserName() -> String? {
        if case .success(let data) = onEnum(of: self.model.replyState),
           let status = data.data.peek(index: 0) as? UiStatus.XQT {
            status.user.rawHandle
        } else {
            nil
        }
    }
}

@Observable
class MediaViewModel {
    var selectedItems: [PhotosPickerItem] = []
    var items: [MediaItem] = []
    var sensitive = false
    func update() {
        if selectedItems.count > 4 {
            selectedItems = Array(selectedItems[(selectedItems.count - 4)...(selectedItems.count - 1)])
        } else {
            selectedItems = selectedItems
        }
        items = selectedItems.map { item in
            MediaItem(item: item)
        }
    }
    func remove(item: MediaItem) {
        if let index = items.firstIndex(of: item) {
            items.remove(at: index)
            selectedItems.remove(at: index)
        }
    }
}

@Observable
class MediaItem: Equatable {
    static func == (lhs: MediaItem, rhs: MediaItem) -> Bool {
        lhs.item == rhs.item
    }
    let item: PhotosPickerItem
    var image: UIImage?
    var data: Data?
    init(item: PhotosPickerItem) {
        self.item = item
        item.loadTransferable(type: Data.self) { result in
            do {
                if let data = try result.get() {
                    if let uiImage = UIImage(data: data) {
                        DispatchQueue.main.async {
                            self.data = data
                            self.image = uiImage
                        }
                    }
                }
            } catch {
            }
        }
    }
}

@Observable
class PollViewModel {
    var enabled = false
    var pollType = ComposePollType.single
    var choices: [PollChoice] = [PollChoice(), PollChoice()]
    var expired = ComposePollExpired.minutes5
    let allExpiration = [
        ComposePollExpired.minutes5,
        ComposePollExpired.minutes30,
        ComposePollExpired.hours1,
        ComposePollExpired.hours6,
        ComposePollExpired.hours12,
        ComposePollExpired.days1,
        ComposePollExpired.days3,
        ComposePollExpired.days7
    ]
    func add() {
        if choices.count < 4 {
            choices.append(PollChoice())
        }
    }
    func remove(choice: PollChoice) {
        if choices.count > 2 {
            choices.removeAll { value in
                value.id == choice.id
            }
        }
    }
}

@Observable
class PollChoice: Identifiable {
    var text = ""
}

enum ComposePollType {
    case single
    case multiple
}

enum ComposePollExpired: String {
    case minutes5
    case minutes30
    case hours1
    case hours6
    case hours12
    case days1
    case days3
    case days7
    var inWholeMilliseconds: Int64 {
        switch self {
        case .minutes5:
            return 5 * 60 * 1000
        case .minutes30:
            return 30 * 60 * 1000
        case .hours1:
            return 1 * 60 * 60 * 1000
        case .hours6:
            return 6 * 60 * 60 * 1000
        case .hours12:
            return 12 * 60 * 60 * 1000
        case .days1:
            return 24 * 60 * 60 * 1000
        case .days3:
            return 3 * 24 * 60 * 60 * 1000
        case .days7:
            return 7 * 24 * 60 * 60 * 1000
        }
    }
}
