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

    // remove handle first @, for example: @example@twitter.com  --> example@twitter.com
    var handleWithoutFirstAt: String {
        handle.hasPrefix("@") ? String(handle.dropFirst()) : handle
    }
}

extension String {
    // remove handle first @, for example: @example@twitter.com  --> example@twitter.com

    func removingHandleFirstPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}

 

