import AVFoundation
import CoreGraphics
import ImageIO
import XCTest

@testable import FlareAppleUI

final class UgoiraEncoderTests: XCTestCase {
    func testVariableTimingWhiteBackgroundAndCancellation() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("ugoira-test-\(UUID())")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let context = try XCTUnwrap(
            CGContext(
                data: nil, width: 63, height: 65, bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        let image = try XCTUnwrap(context.makeImage())
        let frames = try [40, 80, 100].enumerated().map { index, delay in
            let url = directory.appendingPathComponent("\(index).png")
            let destination = try XCTUnwrap(CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil))
            CGImageDestinationAddImage(destination, image, nil)
            XCTAssertTrue(CGImageDestinationFinalize(destination))
            return UgoiraExportFrame(path: url.path, delay: Int64(delay))
        }
        let output = directory.appendingPathComponent("animation.mp4")
        try await UgoiraEncoder.encode(frames: frames, destination: output, smaller: false, progress: { _ in })
        let asset = AVURLAsset(url: output)
        let duration = try await asset.load(.duration)
        XCTAssertEqual(duration.seconds, 0.22, accuracy: 0.001)
        let tracks = try await asset.load(.tracks)
        XCTAssertEqual(tracks.count, 1)
        let track = try XCTUnwrap(tracks.first)
        let size = try await track.load(.naturalSize)
        XCTAssertEqual(size, CGSize(width: 64, height: 66))
        let reader = try AVAssetReader(asset: asset)
        let samples = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
        reader.add(samples)
        XCTAssertTrue(reader.startReading())
        var timestamps: [Double] = []
        while let sample = samples.copyNextSampleBuffer() {
            if CMSampleBufferGetNumSamples(sample) > 0, CMSampleBufferGetTotalSampleSize(sample) > 0 {
                timestamps.append(CMSampleBufferGetPresentationTimeStamp(sample).seconds)
            }
        }
        XCTAssertEqual(timestamps.count, 3)
        for (actual, expected) in zip(timestamps, [0.0, 0.04, 0.12]) { XCTAssertEqual(actual, expected, accuracy: 0.001) }
        let decoded = try await AVAssetImageGenerator(asset: asset).image(at: .zero).image
        let pixel = try XCTUnwrap(
            CGContext(
                data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        pixel.draw(decoded, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        let bytes = try XCTUnwrap(pixel.data).assumingMemoryBound(to: UInt8.self)
        XCTAssertTrue(bytes[0] > 240 && bytes[1] > 240 && bytes[2] > 240)

        let canceled = directory.appendingPathComponent("canceled.mp4")
        let task = Task {
            try await UgoiraEncoder.encode(frames: frames, destination: canceled, smaller: false) { _ in
                withUnsafeCurrentTask { $0?.cancel() }
            }
        }
        do {
            try await task.value
            XCTFail("Canceled encoding should throw")
        } catch is CancellationError { XCTAssertFalse(FileManager.default.fileExists(atPath: canceled.path)) }
    }
}
