//
// PlaybackState.swift
//
//
// Created by MainasuK on 2022-5-12.
//

import Foundation

public enum PlaybackState: Int {
    case unknown = 0
    case buffering = 1
    case readyToPlay = 2
    case playing = 3
    case paused = 4
    case stopped = 5
    case failed = 6
}

// - CustomStringConvertible

extension PlaybackState: CustomStringConvertible {
    public var description: String {
        switch self {
        case .unknown: "unknown"
        case .buffering: "buffering"
        case .readyToPlay: "readyToPlay"
        case .playing: "playing"
        case .paused: "paused"
        case .stopped: "stopped"
        case .failed: "failed"
        }
    }
}
