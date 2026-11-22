// Vendored from AntNexo/antnexo-swift v1.1.0 (4083df99663b04636effe741c7222ee3bc6773fc) by scripts/vendor.sh. Do not edit.
/// The size an image is scaled to so its longest side is at most `max` pixels (the screenshot stays under the upload
/// limit); smaller images keep their size.
public func fittedSize(width: Int, height: Int, max: Int = 1600) -> (width: Int, height: Int) {
    let longest = Swift.max(width, height)
    guard longest > max else { return (width, height) }
    let k = Double(max) / Double(longest)
    return (Swift.max(1, Int((Double(width) * k).rounded())), Swift.max(1, Int((Double(height) * k).rounded())))
}
