import SwiftUI

/// 简单的线图组件
/// 使用原生SwiftUI实现，不依赖Charts框架
struct SimpleLineChart: View {
    let data: [Double]
    let color: Color
    let threshold: Double?
    let maxValue: Double
    let unit: String

    init(data: [Double], color: Color, threshold: Double? = nil, maxValue: Double, unit: String = "%") {
        self.data = data
        self.color = color
        self.threshold = threshold
        self.maxValue = maxValue
        self.unit = unit
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                RoundedRectangle(cornerRadius: 8)
                    .fill(.gray.opacity(0.05))

                // Grid lines
                VStack(spacing: 0) {
                    ForEach(0 ..< 5) { index in
                        if index > 0 {
                            Rectangle()
                                .fill(.gray.opacity(0.2))
                                .frame(height: 0.5)
                        }
                        Spacer()
                    }
                }
                .padding(.horizontal, 8)

                // Y-axis labels
                HStack {
                    VStack {
                        ForEach(0 ..< 5) { index in
                            let value = maxValue * (1.0 - Double(index) / 4.0)
                            Text("\(Int(value))\(unit)")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            if index < 4 {
                                Spacer()
                            }
                        }
                    }
                    .frame(width: 40)

                    Spacer()
                }

                // Threshold line
                if let threshold {
                    let thresholdY = geometry.size.height * (1.0 - threshold / maxValue)
                    Path { path in
                        path.move(to: CGPoint(x: 40, y: thresholdY))
                        path.addLine(to: CGPoint(x: geometry.size.width - 8, y: thresholdY))
                    }
                    .stroke(.red.opacity(0.6), style: StrokeStyle(lineWidth: 1, dash: [5, 5]))
                }

                // Data line
                if !data.isEmpty {
                    let chartWidth = geometry.size.width - 48
                    let chartHeight = geometry.size.height - 16

                    // Line path
                    Path { path in
                        let points = dataPoints(in: CGSize(width: chartWidth, height: chartHeight))
                        if let firstPoint = points.first {
                            path.move(to: CGPoint(x: firstPoint.x + 40, y: firstPoint.y + 8))
                            for point in points.dropFirst() {
                                path.addLine(to: CGPoint(x: point.x + 40, y: point.y + 8))
                            }
                        }
                    }
                    .stroke(color, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))

                    // Area fill
                    Path { path in
                        let points = dataPoints(in: CGSize(width: chartWidth, height: chartHeight))
                        if let firstPoint = points.first {
                            path.move(to: CGPoint(x: firstPoint.x + 40, y: chartHeight + 8))
                            path.addLine(to: CGPoint(x: firstPoint.x + 40, y: firstPoint.y + 8))
                            for point in points.dropFirst() {
                                path.addLine(to: CGPoint(x: point.x + 40, y: point.y + 8))
                            }
                            if let lastPoint = points.last {
                                path.addLine(to: CGPoint(x: lastPoint.x + 40, y: chartHeight + 8))
                            }
                            path.closeSubpath()
                        }
                    }
                    .fill(LinearGradient(
                        colors: [color.opacity(0.3), color.opacity(0.1)],
                        startPoint: .top,
                        endPoint: .bottom
                    ))

                    // Data points
                    ForEach(Array(dataPoints(in: CGSize(width: chartWidth, height: chartHeight)).enumerated()), id: \.offset) { _, point in
                        Circle()
                            .fill(color)
                            .frame(width: 4, height: 4)
                            .position(x: point.x + 40, y: point.y + 8)
                    }
                }
            }
        }
        .clipped()
    }

    private func dataPoints(in size: CGSize) -> [CGPoint] {
        guard !data.isEmpty else { return [] }

        let stepX = size.width / max(1, Double(data.count - 1))

        return data.enumerated().map { index, value in
            let x = Double(index) * stepX
            let normalizedValue = min(max(value / maxValue, 0), 1)
            let y = size.height * (1.0 - normalizedValue)
            return CGPoint(x: x, y: y)
        }
    }
}

// - Preview

#Preview {
    VStack(spacing: 20) {
        SimpleLineChart(
            data: [30, 45, 60, 40, 70, 55, 80, 65, 50, 75],
            color: .green,
            threshold: 50,
            maxValue: 100,
            unit: "%"
        )
        .frame(height: 120)

        SimpleLineChart(
            data: [120, 150, 180, 160, 200, 175, 220, 190, 170, 210],
            color: .blue,
            threshold: 200,
            maxValue: 250,
            unit: "MB"
        )
        .frame(height: 120)
    }
    .padding()
}
