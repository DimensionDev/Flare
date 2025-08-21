
func formatCount(_ count: Int64) -> String {
    if count == 0 {
        return ""
    }
    if count < 1000 {
        return "\(count)"
    }
    if count < 1_000_000 {
        let k = Int(count / 1000)
        return "\(k)K"
    }
    let m = Int(count / 1_000_000)
    return "\(m)M"
}
