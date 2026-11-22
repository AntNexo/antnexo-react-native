#if DEBUG
import Foundation
@_spi(Testing) import AntNexoReactNative

/// Scripted simulator checks, example app only: with `-probe 1` it waits for the sheet the JS scenario opened
/// (`-scenario show`), logs the bridge the page got, then runs `-js1 "<code>"` … `-js9` in the page 3 s apart, logging
/// to Documents/probe.log (`xcrun simctl get_app_container booted antnexo.reactnative.example data`).
@MainActor
enum SheetProbe {
  static let file = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    .appendingPathComponent("probe.log")

  static func log(_ s: String) {
    let line = "\(ISO8601DateFormatter().string(from: Date())) \(s)\n"
    if let h = try? FileHandle(forWritingTo: file) {
      h.seekToEndOfFile()
      h.write(Data(line.utf8))
      try? h.close()
    } else {
      try? line.write(to: file, atomically: true, encoding: .utf8)
    }
  }

  static func js(_ code: String) async -> String {
    do {
      return try await AntNexo.evaluateInSheet(code).map { "\($0)" } ?? "nil"
    } catch {
      return "error: \(error)"
    }
  }

  static func wait(_ s: Double) async { try? await Task.sleep(nanoseconds: UInt64(s * 1e9)) }

  static func runIfAsked() async {
    let d = UserDefaults.standard
    guard d.bool(forKey: "probe") else { return }
    try? FileManager.default.removeItem(at: file)
    await wait(d.double(forKey: "wait") > 0 ? d.double(forKey: "wait") : 15)
    log("location " + (await js("location.href")))
    log("bridge " + (await js("JSON.stringify({v: AntNexoApp.v, platform: AntNexoApp.platform, sdk: AntNexoApp.sdk, context: AntNexoApp.context, identify: !!AntNexoApp.identify, screenshot: !!AntNexoApp.screenshot})")))
    for i in 1...9 {
      guard let code = d.string(forKey: "js\(i)") else { break }
      log("js\(i) " + (await js(code)))
      await wait(3)
    }
    log("done")
  }
}
#endif
