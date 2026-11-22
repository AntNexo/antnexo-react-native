// Vendored from AntNexo/antnexo-swift v1.1.0 (4083df99663b04636effe741c7222ee3bc6773fc) by scripts/vendor.sh. Do not edit.
/// A locale identifier as BCP 47 (`en_CZ` → `en-CZ`), without `@` keywords, at most 35 characters (the API's limit).
public func bcp47(_ identifier: String) -> String {
    let base = identifier.split(separator: "@", maxSplits: 1, omittingEmptySubsequences: false).first ?? ""
    let tag = base.replacingOccurrences(of: "_", with: "-")
    return tag.isEmpty ? "und" : String(tag.prefix(35))
}
