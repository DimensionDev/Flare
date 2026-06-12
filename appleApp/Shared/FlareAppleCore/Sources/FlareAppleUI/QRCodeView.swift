import CoreImage.CIFilterBuiltins
import SwiftUI

public struct QRCodeView: View {
    private let text: String

    public init(text: String) {
        self.text = text
    }

    public var body: some View {
        if let image = makeQRCode(from: text) {
            Image(decorative: image, scale: 1, orientation: .up)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .padding(12)
        } else {
            Image(systemName: "qrcode")
                .resizable()
                .scaledToFit()
                .padding(40)
                .foregroundStyle(.secondary)
        }
    }

    private func makeQRCode(from text: String) -> CGImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)

        guard let outputImage = filter.outputImage else {
            return nil
        }

        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        return CIContext().createCGImage(scaledImage, from: scaledImage.extent)
    }
}
