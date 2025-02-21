
func formatCount(_ count: Int64) -> String {
    if count == 0 {
        return ""
    }
    if count < 1000 {
        return "\(count)"
    }
    if count < 1_000_000 {
        let k = Double(count) / 1000.0
        return String(format: "%.1fK", k).replacingOccurrences(of: ".0", with: "")
    }
    let m = Double(count) / 1_000_000.0
    return String(format: "%.1fM", m).replacingOccurrences(of: ".0", with: "")
}
