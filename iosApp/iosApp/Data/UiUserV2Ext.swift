extension UiUserV2 {
    var isMastodon: Bool {
        return platformType == .mastodon
    }
    
    var isMisskey: Bool {
        return platformType == .misskey
    }
    
    var isBluesky: Bool {
        return platformType == .bluesky
    }
    
    var isXQt: Bool {
        return platformType == .xQt
    }
    
    var isVVo: Bool {
        return platformType == .vvo
    }
}
