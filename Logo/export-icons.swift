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

// Small system symbols use a horn, not the mascot's full-body silhouette.
// This 16pt path is the source for both vector PDFs/SVG and template PNGs.
// The open crescent inside the horn remains visible at menu-bar sizes.
let horn = CGMutablePath()
horn.move(to: CGPoint(x: 8.7, y: 1))
horn.addCurve(to: CGPoint(x: 10.9, y: 14.1),
              control1: CGPoint(x: 12.8, y: 4.2), control2: CGPoint(x: 14.6, y: 11.3))
horn.addCurve(to: CGPoint(x: 3, y: 10.8),
              control1: CGPoint(x: 8.3, y: 15.9), control2: CGPoint(x: 3.2, y: 13.6))
horn.addCurve(to: CGPoint(x: 6.9, y: 5.2),
              control1: CGPoint(x: 2.9, y: 9.4), control2: CGPoint(x: 5.6, y: 7.7))
horn.addCurve(to: CGPoint(x: 8, y: 1.3),
              control1: CGPoint(x: 7.7, y: 3.6), control2: CGPoint(x: 8.1, y: 2.1))
horn.addCurve(to: CGPoint(x: 8.7, y: 1),
              control1: CGPoint(x: 7.9, y: 0.7), control2: CGPoint(x: 8.2, y: 0.6))
horn.closeSubpath()
horn.move(to: CGPoint(x: 9.1, y: 4.1))
horn.addCurve(to: CGPoint(x: 10.4, y: 12.8),
              control1: CGPoint(x: 12, y: 7.4), control2: CGPoint(x: 12.6, y: 11))
horn.addCurve(to: CGPoint(x: 9.8, y: 12.2),
              control1: CGPoint(x: 9.8, y: 13.3), control2: CGPoint(x: 9.5, y: 12.8))
horn.addCurve(to: CGPoint(x: 9.1, y: 4.1),
              control1: CGPoint(x: 11, y: 10), control2: CGPoint(x: 10.2, y: 6.7))
horn.closeSubpath()

func drawHorn(in context: CGContext, size: CGFloat, white: Bool = false) {
    context.saveGState()
    context.translateBy(x: 0, y: size)
    context.scaleBy(x: size / 16, y: -size / 16)
    context.setFillColor(CGColor(gray: white ? 1 : 0, alpha: 1))
    context.addPath(horn)
    context.drawPath(using: .eoFill)
    context.restoreGState()
}

func hornPNG(_ path: String, size: Int) {
    let context = bitmap(size)
    drawHorn(in: context, size: CGFloat(size))
    guard let image = context.makeImage() else { fail("Cannot render horn template.") }
    writePNG(image, to: root.appendingPathComponent(path))
}

func hornPDF(_ path: String, pages: Int = 1) {
    let url = root.appendingPathComponent(path)
    var bounds = CGRect(x: 0, y: 0, width: 16, height: 16)
    guard let context = CGContext(url as CFURL, mediaBox: &bounds, nil) else { fail("Cannot write \(path)") }
    for page in 0..<pages {
        context.beginPDFPage(nil)
        drawHorn(in: context, size: bounds.width, white: page > 0)
        context.endPDFPage()
    }
    context.closePDF()
    guard let document = CGPDFDocument(url as CFURL), document.numberOfPages == pages,
          document.page(at: 1)?.getBoxRect(.mediaBox) == bounds else {
        fail("Input-source PDF must keep its 16pt page contract: \(path)")
    }
}

// Export the same path for future small-symbol consumers without a second
// hand-maintained version of the artwork.
var hornSVGPath = ""
func svgPoint(_ point: CGPoint) -> String {
    String(format: "%.1f %.1f", locale: Locale(identifier: "en_US_POSIX"), point.x, point.y)
}
horn.applyWithBlock { element in
    let points = element.pointee.points
    switch element.pointee.type {
    case .moveToPoint: hornSVGPath += "M \(svgPoint(points[0])) "
    case .addLineToPoint: hornSVGPath += "L \(svgPoint(points[0])) "
    case .addQuadCurveToPoint: hornSVGPath += "Q \(svgPoint(points[0])) \(svgPoint(points[1])) "
    case .addCurveToPoint: hornSVGPath += "C \(svgPoint(points[0])) \(svgPoint(points[1])) \(svgPoint(points[2])) "
    case .closeSubpath: hornSVGPath += "Z "
    @unknown default: fail("Unsupported horn path element.")
    }
}
let hornSVG = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
  <!-- Generated by Logo/export-icons.swift. -->
  <path fill="currentColor" fill-rule="evenodd" d="\(hornSVGPath.trimmingCharacters(in: .whitespaces))"/>
</svg>

"""
do { try hornSVG.write(to: root.appendingPathComponent("Logo/rhino-horn.svg"), atomically: true, encoding: .utf8) }
catch { fail("Cannot export horn SVG: \(error)") }

let macSizes = [
    ("16x16", 16), ("16x16@2x", 32), ("32x32", 32), ("32x32@2x", 64),
    ("128x128", 128), ("128x128@2x", 256), ("256x256", 256),
    ("256x256@2x", 512), ("512x512", 512), ("512x512@2x", 1024),
]
for (name, size) in macSizes { png("Logo/AppIcon.iconset/icon_\(name).png", size: size) }
hornPNG("Logo/menubar-template.png", size: 36)
hornPNG("Resources/menubar-template.png", size: 36)
hornPDF("Logo/inputsource.pdf")
hornPDF("Logo/menuicon.pdf", pages: 2)
hornPDF("Resources/etinput.pdf")
// Production IMK input menus require a single-page template; AppKit tints it.
hornPDF("Resources/etinput-menu.pdf")

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
print("Verified mascot PNG sizes and vector horn PDFs; IMK pages remain 16pt.")
