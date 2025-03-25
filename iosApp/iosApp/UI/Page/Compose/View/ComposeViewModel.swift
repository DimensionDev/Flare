import Foundation
import PhotosUI
import shared
import SwiftUI

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
    var status: shared.ComposeStatus?
    var showEmoji = false
    init(accountType: AccountType, status: shared.ComposeStatus?) {
        self.status = status
        presenter = .init(accountType: accountType, status: status)
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
            if case let .success(account) = onEnum(of: self.model.account) {
                let data: ComposeData = .init(
                    account: account.data,
                    content: text,
                    visibility: getVisibility(),
                    language: ["en"],
                    medias: getMedia(),
                    sensitive: mediaViewModel.sensitive,
                    spoilerText: contentWarning,
                    poll: getPoll(),
                    localOnly: false,
                    referenceStatus: getReferenceStatus()
                )
                model.send(data: data)
            }
        }
    }

    private func getMedia() -> [FileItem] {
        mediaViewModel.items.map { item in
            FileItem(name: item.item.itemIdentifier, data: KotlinByteArray.from(data: item.data!))
        }
    }

    private func getReferenceStatus() -> ComposeData.ReferenceStatus? {
        if let data = status, let replyState = model.replyState, case let .success(timeline) = onEnum(of: replyState) {
            ComposeData.ReferenceStatus(data: timeline.data, composeStatus: data)
        } else {
            nil
        }
    }

    private func getPoll() -> ComposeData.Poll? {
        if pollViewModel.enabled {
            ComposeData.Poll(options: pollViewModel.choices.map { item in item.text }, expiredAfter: pollViewModel.expired.inWholeMilliseconds, multiple: pollViewModel.pollType == ComposePollType.multiple)
        } else {
            nil
        }
    }

    private func getVisibility() -> UiTimelineItemContentStatusTopEndContentVisibility.Type_ {
        if case let .success(data) = onEnum(of: model.visibilityState) {
            data.data.visibility
        } else {
            UiTimelineItemContentStatusTopEndContentVisibility.Type_.public
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
            selectedItems = Array(selectedItems[(selectedItems.count - 4) ... (selectedItems.count - 1)])
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
            } catch {}
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
        ComposePollExpired.days7,
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
            5 * 60 * 1000
        case .minutes30:
            30 * 60 * 1000
        case .hours1:
            1 * 60 * 60 * 1000
        case .hours6:
            6 * 60 * 60 * 1000
        case .hours12:
            12 * 60 * 60 * 1000
        case .days1:
            24 * 60 * 60 * 1000
        case .days3:
            3 * 24 * 60 * 60 * 1000
        case .days7:
            7 * 24 * 60 * 60 * 1000
        }
    }
}
