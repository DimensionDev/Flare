extension UiUserV2 {
    var isMastodon: Bool {
        platformType == .mastodon
    }

    var isMisskey: Bool {
        platformType == .misskey
    }

    var isBluesky: Bool {
        platformType == .bluesky
    }

    var isXQt: Bool {
        platformType == .xQt
    }

    var isVVo: Bool {
        platformType == .vvo
    }
 
}
