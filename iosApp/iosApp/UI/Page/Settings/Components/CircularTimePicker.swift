import SwiftUI

struct CircularTimePicker: View {
    @Binding var selectedTime: Date
    @State private var dragOffset: CGSize = .zero
    @State private var currentAngle: Double = 0
    @State private var lastHapticAngle: Double = -1
    @Environment(FlareTheme.self) private var theme

    private let radius: CGFloat = 120
    private let knobSize: CGFloat = 30

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                Text(timeString)
                    .font(.system(size: 48, weight: .light, design: .monospaced))
                    .foregroundColor(theme.labelColor)

                Text("end time")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            ZStack {
                Circle()
                    .stroke(theme.secondaryBackgroundColor, lineWidth: 4)
                    .frame(width: radius * 2, height: radius * 2)

                ForEach(0 ..< 12) { hour in
                    let angle = Double(hour) * 30 - 90
                    let x = cos(angle * .pi / 180) * (radius - 20)
                    let y = sin(angle * .pi / 180) * (radius - 20)

                    Text("\(hour == 0 ? 12 : hour)")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.secondary)
                        .position(x: radius + x, y: radius + y)
                }

                ForEach(0 ..< 12) { minute in
                    let angle = Double(minute) * 30 - 90
                    let x = cos(angle * .pi / 180) * (radius - 35)
                    let y = sin(angle * .pi / 180) * (radius - 35)

                    Text("\(minute * 5)")
                        .font(.system(size: 12))
                        .foregroundColor(Color.secondary.opacity(0.6))
                        .position(x: radius + x, y: radius + y)
                }

                Path { path in
                    let x = cos(currentAngle * .pi / 180) * radius
                    let y = sin(currentAngle * .pi / 180) * radius
                    path.move(to: CGPoint(x: radius, y: radius))
                    path.addLine(to: CGPoint(x: radius + x, y: radius + y))
                }
                .stroke(theme.tintColor, lineWidth: 2)

                Circle()
                    .fill(theme.tintColor)
                    .frame(width: knobSize, height: knobSize)
                    .position(knobPosition)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                updateAngle(from: value.location)
                            }
                    )

                Circle()
                    .fill(theme.tintColor)
                    .frame(width: 8, height: 8)
                    .position(x: radius, y: radius)
            }
            .frame(width: radius * 2, height: radius * 2)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        updateAngle(from: value.location)
                    }
            )
            .onAppear {
                updateAngleFromTime()
            }
        }
    }

    private var timeString: String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.locale = Locale.current
        return formatter.string(from: selectedTime)
    }

    private var knobPosition: CGPoint {
        let x = cos(currentAngle * .pi / 180) * radius + radius
        let y = sin(currentAngle * .pi / 180) * radius + radius
        return CGPoint(x: x, y: y)
    }

    private func updateAngle(from location: CGPoint) {
        let center = CGPoint(x: radius, y: radius)
        let vector = CGPoint(x: location.x - center.x, y: location.y - center.y)
        let angle = atan2(vector.y, vector.x) * 180 / .pi

        let normalizedAngle = angle < 0 ? angle + 360 : angle
        let snappedAngle = round(normalizedAngle / 30) * 30

        if abs(snappedAngle - lastHapticAngle) >= 30 {
            #if os(iOS)
                let impactFeedback = UIImpactFeedbackGenerator(style: .light)
                impactFeedback.impactOccurred()
            #endif
            lastHapticAngle = snappedAngle
        }

        withAnimation(.easeOut(duration: 0.1)) {
            currentAngle = snappedAngle
        }
        updateTimeFromAngle()
    }

    private func updateAngleFromTime() {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.hour, .minute], from: selectedTime)
        let hour = components.hour ?? 0
        let minute = components.minute ?? 0

        let totalMinutes = (hour % 12) * 60 + minute
        let calculatedAngle = Double(totalMinutes) * 0.5 - 90

        currentAngle = calculatedAngle < 0 ? calculatedAngle + 360 : calculatedAngle
        lastHapticAngle = currentAngle
    }

    private func updateTimeFromAngle() {
        let adjustedAngle = currentAngle + 90
        let normalizedAngle = adjustedAngle < 0 ? adjustedAngle + 360 : adjustedAngle
        let totalMinutes = Int(normalizedAngle * 2)

        let hour = (totalMinutes / 60) % 12
        let minute = (totalMinutes % 60) / 5 * 5

        let calendar = Calendar.current
        var components = calendar.dateComponents([.year, .month, .day, .timeZone], from: selectedTime)

        if hour == 0 {
            components.hour = 12
        } else {
            components.hour = hour
        }
        components.minute = minute
        components.second = 0

        if let newTime = calendar.date(from: components) {
            selectedTime = newTime
        }
    }
}

struct TimeRangePickerSheet: View {
    @Binding var timeRange: SensitiveContentTimeRange?
    @Binding var isPresented: Bool
    @State private var tempStartTime: Date = .init()
    @State private var tempEndTime: Date = .init()
    @State private var isEditingEndTime: Bool = true
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        NavigationView {
            VStack(spacing: 30) {
                VStack(spacing: 8) {
                    Text("Time Range")
                        .font(.headline)
                        .foregroundColor(theme.labelColor)

                    HStack(spacing: 8) {
                        VStack(spacing: 4) {
                            Text("Start")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(formatTime(tempStartTime))
                                .font(.title2)
                                .foregroundColor(isEditingEndTime ? .secondary : theme.tintColor)
                                .fontWeight(isEditingEndTime ? .regular : .bold)
                        }

                        Text("—")
                            .font(.title2)
                            .foregroundColor(.secondary)

                        VStack(spacing: 4) {
                            Text("End")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(formatTime(tempEndTime))
                                .font(.title2)
                                .foregroundColor(isEditingEndTime ? theme.tintColor : .secondary)
                                .fontWeight(isEditingEndTime ? .bold : .regular)
                        }
                    }
                }
                .padding()
                .background(theme.secondaryBackgroundColor)
                .cornerRadius(12)

                CircularTimePicker(selectedTime: isEditingEndTime ? $tempEndTime : $tempStartTime)

                HStack(spacing: 20) {
                    Button(action: {
                        isEditingEndTime = false
                    }) {
                        Text("Edit Start Time")
                            .foregroundColor(isEditingEndTime ? .secondary : theme.tintColor)
                            .font(.headline)
                    }
                    .buttonStyle(PlainButtonStyle())

                    Button(action: {
                        isEditingEndTime = true
                    }) {
                        Text("Edit End Time")
                            .foregroundColor(isEditingEndTime ? theme.tintColor : .secondary)
                            .font(.headline)
                    }
                    .buttonStyle(PlainButtonStyle())
                }

                Spacer()

                HStack(spacing: 20) {
                    Button(action: {
                        isPresented = false
                    }) {
                        Text("Cancel")
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(theme.secondaryBackgroundColor)
                            .cornerRadius(12)
                    }
                    .buttonStyle(PlainButtonStyle())

                    Button(action: {
                        saveTimeRange()
                        isPresented = false
                    }) {
                        Text("OK")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(theme.tintColor)
                            .cornerRadius(12)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(.horizontal)
            }
            .padding()
            .background(theme.primaryBackgroundColor)
            .navigationTitle("Set Time Range")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                setupInitialTimes()
            }
        }
    }

    private func setupInitialTimes() {
        if let range = timeRange {
            tempStartTime = range.startTime
            tempEndTime = range.endTime
        } else {
            tempStartTime = Date()
            tempEndTime = Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date()
        }
    }

    private func formatTime(_ time: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.locale = Locale.current
        return formatter.string(from: time)
    }

    private func saveTimeRange() {
        timeRange = SensitiveContentTimeRange(
            startTime: tempStartTime,
            endTime: tempEndTime,
            isEnabled: true
        )
    }
}
