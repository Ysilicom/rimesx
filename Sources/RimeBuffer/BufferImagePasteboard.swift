import AppKit

/// Decode clipboard pixels once at the trusted paste boundary. The stored
/// attachment is a bounded PNG, never an arbitrary file URL or pasteboard
/// provider that can be consulted again after the focus lease changes.
enum BufferImagePasteboard {
    static func read(_ pasteboard: NSPasteboard) -> BufferModel.ImageAttachment? {
        let types: [NSPasteboard.PasteboardType] = [
            .png, .tiff, NSPasteboard.PasteboardType("public.jpeg"),
        ]
        guard let type = types.first(where: { pasteboard.availableType(from: [$0]) != nil }),
              let raw = pasteboard.data(forType: type),
              !raw.isEmpty, raw.count <= 8 * 1_048_576,
              let source = NSBitmapImageRep(data: raw),
              source.pixelsWide > 0, source.pixelsHigh > 0,
              source.pixelsWide <= 8192, source.pixelsHigh <= 8192,
              Double(source.pixelsWide) * Double(source.pixelsHigh) <= 16_000_000 else {
            return nil
        }
        let scale = min(1.0, 1600.0 / Double(max(source.pixelsWide, source.pixelsHigh)))
        let width = max(1, Int(Double(source.pixelsWide) * scale))
        let height = max(1, Int(Double(source.pixelsHigh) * scale))
        let image = NSImage(size: NSSize(width: width, height: height))
        image.lockFocus()
        source.draw(in: NSRect(x: 0, y: 0, width: width, height: height))
        image.unlockFocus()
        guard let tiff = image.tiffRepresentation,
              let bitmap = NSBitmapImageRep(data: tiff),
              let png = bitmap.representation(using: .png, properties: [:]),
              png.count <= 4 * 1_048_576 else { return nil }
        return BufferModel.ImageAttachment(
            pngData: png, pixelWidth: width, pixelHeight: height
        )
    }
}
