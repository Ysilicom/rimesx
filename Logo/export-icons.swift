import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

let root = URL(fileURLWithPath: CommandLine.arguments[1]).standardizedFileURL
let temporary = URL(fileURLWithPath: CommandLine.arguments[2])
let sourceURL = root.appendingPathComponent("Logo/rimes-rhino.png")
guard let source = CGImageSourceCreateWithURL(sourceURL as CFURL, nil),
      let original = CGImageSourceCreateImageAtIndex(source, 0, nil),
      original.width == original.height, original.width >= 1024 else {
    fail("The shared master must be a square PNG of at least 1024 pixels.")
}
let rgb = CGColorSpace(name: CGColorSpace.sRGB)!

func bitmap(_ size: Int, opaque: Bool = false) -> CGContext {
    let alpha: CGImageAlphaInfo = opaque ? .noneSkipLast : .premultipliedLast
    guard let context = CGContext(data: nil, width: size, height: size,
                                  bitsPerComponent: 8, bytesPerRow: size * 4,
                                  space: rgb,
                                  bitmapInfo: alpha.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else {
        fail("Cannot allocate \(size)-pixel icon.")
    }
    context.interpolationQuality = .high
    return context
}

// Size the character itself rather than the transparent source canvas.
let sourceBitmap = bitmap(original.width)
sourceBitmap.draw(original, in: CGRect(x: 0, y: 0, width: original.width, height: original.height))
let bytes = sourceBitmap.data!.assumingMemoryBound(to: UInt8.self)
var minX = original.width, minY = original.height, maxX = -1, maxY = -1
for y in 0..<original.height {
    for x in 0..<original.width where bytes[y * sourceBitmap.bytesPerRow + x * 4 + 3] > 0 {
        minX = min(minX, x); maxX = max(maxX, x)
        minY = min(minY, y); maxY = max(maxY, y)
    }
}
guard maxX > minX, maxY > minY, minX > 0, minY > 0,
      maxX < original.width - 1, maxY < original.height - 1,
      let normalized = sourceBitmap.makeImage(),
      let artwork = normalized.cropping(to: CGRect(x: minX, y: minY,
                                                    width: maxX - minX + 1, height: maxY - minY + 1)) else {
    fail("The master must contain a complete character with transparent margins.")
}

let mobileBackground = CGColor(colorSpace: rgb, components: [247.0 / 255, 245.0 / 255, 241.0 / 255, 1])!

func rendered(_ size: Int, occupancy: CGFloat = 0.92,
              opaque: Bool = false, monochrome: Bool = false) -> CGImage {
    let context = bitmap(size, opaque: opaque)
    let canvas = CGRect(x: 0, y: 0, width: size, height: size)
    if opaque {
        context.setFillColor(mobileBackground)
        context.fill(canvas)
    }
    let scale = CGFloat(size) * occupancy / CGFloat(max(artwork.width, artwork.height))
    let width = CGFloat(artwork.width) * scale, height = CGFloat(artwork.height) * scale
    context.draw(artwork, in: CGRect(x: (CGFloat(size) - width) / 2,
                                    y: (CGFloat(size) - height) / 2,
                                    width: width, height: height))
    if monochrome {
        context.setBlendMode(.sourceIn)
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        context.fill(canvas)
    }
    guard let image = context.makeImage() else { fail("Cannot render icon.") }
    return image
}

func writePNG(_ image: CGImage, to url: URL) {
    do { try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true) }
    catch { fail("Cannot create \(url.path): \(error)") }
    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fail("Cannot encode \(url.path)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination),
          let check = CGImageSourceCreateWithURL(url as CFURL, nil),
          let decoded = CGImageSourceCreateImageAtIndex(check, 0, nil),
          decoded.width == image.width, decoded.height == image.height else {
        fail("PNG verification failed: \(url.path)")
    }
}

func png(_ path: String, size: Int, occupancy: CGFloat = 0.92,
         opaque: Bool = false, monochrome: Bool = false) {
    writePNG(rendered(size, occupancy: occupancy, opaque: opaque, monochrome: monochrome),
             to: root.appendingPathComponent(path))
}

func pdf(_ path: String, monochrome: Bool, pages: Int = 1) {
    let url = root.appendingPathComponent(path)
    var bounds = CGRect(x: 0, y: 0, width: 16, height: 16)
    guard let context = CGContext(url as CFURL, mediaBox: &bounds, nil) else { fail("Cannot write \(path)") }
    let icon = rendered(256, monochrome: monochrome)
    for page in 0..<pages {
        context.beginPDFPage(nil)
        if page == 0 {
            context.draw(icon, in: bounds)
        } else {
            context.clip(to: bounds, mask: icon)
            context.setFillColor(CGColor(gray: 1, alpha: 1))
            context.fill(bounds)
        }
        context.endPDFPage()
    }
    context.closePDF()
    guard let document = CGPDFDocument(url as CFURL), document.numberOfPages == pages,
          document.page(at: 1)?.getBoxRect(.mediaBox) == bounds else {
        fail("Input-source PDF must keep its 16pt page contract: \(path)")
    }
}

let macSizes = [
    ("16x16", 16), ("16x16@2x", 32), ("32x32", 32), ("32x32@2x", 64),
    ("128x128", 128), ("128x128@2x", 256), ("256x256", 256),
    ("256x256@2x", 512), ("512x512", 512), ("512x512@2x", 1024),
]
for (name, size) in macSizes { png("Logo/AppIcon.iconset/icon_\(name).png", size: size) }
png("Logo/menubar-template.png", size: 36, monochrome: true)
png("Resources/menubar-template.png", size: 36, monochrome: true)
pdf("Logo/inputsource.pdf", monochrome: false)
pdf("Logo/menuicon.pdf", monochrome: true, pages: 2)
pdf("Resources/etinput.pdf", monochrome: false)
// Production IMK input menus require a single-page template; AppKit tints it.
pdf("Resources/etinput-menu.pdf", monochrome: true)

png("platforms/ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png", size: 1024, opaque: true)
png("platforms/ios/Resources/Assets.xcassets/BrandLogo.imageset/BrandLogo.png", size: 512)

let android = "platforms/android/app/src/main/res"
png("\(android)/drawable-nodpi/rimes_brand_logo.png", size: 512)
// Adaptive layers are 108dp; the complete mascot fits the central 66dp safe
// area. xxxhdpi gives a 432px layer without extra bitmap-density padding.
png("\(android)/drawable-xxxhdpi/rimes_launcher_foreground.png", size: 432, occupancy: 66.0 / 108)
png("\(android)/drawable-xxxhdpi/rimes_launcher_monochrome.png", size: 432, occupancy: 66.0 / 108, monochrome: true)

for size in [16, 20, 24, 32, 40, 48, 64, 128, 256] {
    writePNG(rendered(size), to: temporary.appendingPathComponent("windows-\(size).png"))
}
for size in [16, 24, 32, 48, 64, 128, 256, 512] {
    png("platforms/linux/ime/data/icons/hicolor/\(size)x\(size)/apps/rimes.png", size: size)
}
print("Verified PNG sizes and IMK PDF page bounds; artwork fills 92% of square exports.")
