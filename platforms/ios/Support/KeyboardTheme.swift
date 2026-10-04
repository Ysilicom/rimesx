import UIKit
import RimesCore

/// Shared by the app preview and the extension. Dynamic colors keep every pet recognizable at night.
struct KeyboardPalette {
    let background: UIColor
    let key: UIColor
    let functional: UIColor
    let accent: UIColor
    var native = false
    let ink: UIColor = .label
    var accentInk: UIColor { native ? .white : Self.contrastingInk(on: accent) }
    var pressedSelected: UIColor { accent.withBrightness(0.78) }
    var pressedSelectedInk: UIColor { Self.contrastingInk(on: pressedSelected) }
    /// For small text on the keyboard background, where a pale pet accent would lack contrast.
    var accentText: UIColor {
        UIColor { traits in
            let color = accent.resolvedColor(with: traits)
            return Self.contrast(color, background.resolvedColor(with: traits)) >= 4.5
                ? color : ink.resolvedColor(with: traits)
        }
    }
    init(background: UInt32, key: UInt32, functional: UInt32, accent: UInt32,
         darkBackground: UInt32, darkKey: UInt32, darkFunctional: UInt32, darkAccent: UInt32, native: Bool = false) {
        self.background = .themeColor(background, darkBackground)
        self.key = .themeColor(key, darkKey)
        self.functional = .themeColor(functional, darkFunctional)
        self.accent = .themeColor(accent, darkAccent)
        self.native = native
    }
    static func contrastingInk(on color: UIColor) -> UIColor {
        UIColor { traits in
            let resolved = color.resolvedColor(with: traits)
            return contrast(.black, resolved) > contrast(.white, resolved) ? .black : .white
        }
    }
    static func contrast(_ a: UIColor, _ b: UIColor) -> CGFloat {
        func luminance(_ color: UIColor) -> CGFloat {
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, alpha: CGFloat = 0
            color.getRed(&r, green: &g, blue: &b, alpha: &alpha)
            func linear(_ c: CGFloat) -> CGFloat { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
            return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        }
        let x = luminance(a), y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
}

extension StatusSkin {
    var keyboardStyle: KeyboardSkin { canonical == .apple ? .system : .rimes }
    var glyph: String {
        switch self {
        case .apple, .light: ""
        case .rhino: "🦏"
        case .crab, .notoCrab: "🦀"
        case .kitten: "🐱"
        case .puppy: "🐶"
        case .piglet: "🐷"
        case .dog: "🐕"
        case .poodle: "🐩"
        case .pig: "🐖"
        case .rabbit: "🐇"
        case .penguin: "🐧"
        case .fox: "🦊"
        case .panda: "🐼"
        case .turtle: "🐢"
        case .octopus: "🐙"
        case .frog: "🐸"
        case .chick: "🐣"
        }
    }
    var palette: KeyboardPalette {
        switch canonical {
        case .apple, .light:
            .init(background: 0xD1D3D9, key: 0xFFFFFF, functional: 0xABB0BA, accent: 0x007AFF,
                  darkBackground: 0x1F1F1F, darkKey: 0x6E6E6E, darkFunctional: 0x404040, darkAccent: 0x0A84FF, native: true)
        case .rhino:
            .init(background: 0xD8D8D1, key: 0xFFF9EB, functional: 0xB8BBB9, accent: 0xC65316,
                  darkBackground: 0x292A28, darkKey: 0x46463F, darkFunctional: 0x5C5E59, darkAccent: 0xF49A53)
        case .crab:
            .init(background: 0xEADDD1, key: 0xFFF7EB, functional: 0xDEC1A7, accent: 0xBE4C3D,
                  darkBackground: 0x312925, darkKey: 0x504039, darkFunctional: 0x655045, darkAccent: 0xF58E72)
        case .kitten:
            .init(background: 0xF1DFC9, key: 0xFFFAF1, functional: 0xEBC797, accent: 0xA45B1E,
                  darkBackground: 0x30271F, darkKey: 0x514131, darkFunctional: 0x66503A, darkAccent: 0xF5B968)
        case .puppy:
            .init(background: 0xE6D9CC, key: 0xFFF9F1, functional: 0xD8BB99, accent: 0x825734,
                  darkBackground: 0x2E2722, darkKey: 0x4F4136, darkFunctional: 0x64503F, darkAccent: 0xDCB384)
        case .piglet:
            .init(background: 0xF0DCE3, key: 0xFFF8FA, functional: 0xE6B6C5, accent: 0xAC4469,
                  darkBackground: 0x32252B, darkKey: 0x533B45, darkFunctional: 0x684552, darkAccent: 0xF0A0BC)
        case .dog:
            .init(background: 0xE9DED0, key: 0xFFF9EF, functional: 0xD9C29F, accent: 0x93602D,
                  darkBackground: 0x2F2820, darkKey: 0x514335, darkFunctional: 0x67533D, darkAccent: 0xEABC7A)
        case .poodle:
            .init(background: 0xDCDDDD, key: 0xFAFBFC, functional: 0xBEC3C7, accent: 0x626A72,
                  darkBackground: 0x272A2D, darkKey: 0x42474C, darkFunctional: 0x555D64, darkAccent: 0xC8D1D8)
        case .pig:
            .init(background: 0xF2DECF, key: 0xFFF9F1, functional: 0xECC0A7, accent: 0xBF5A62,
                  darkBackground: 0x322824, darkKey: 0x544039, darkFunctional: 0x695147, darkAccent: 0xF8BDA4)
        case .rabbit:
            .init(background: 0xE2DFE4, key: 0xFFFAFD, functional: 0xD8C7D2, accent: 0x9D557E,
                  darkBackground: 0x2B272E, darkKey: 0x48414D, darkFunctional: 0x5E4F60, darkAccent: 0xEDB4D5)
        case .notoCrab:
            .init(background: 0xF1DFC8, key: 0xFFFAEE, functional: 0xF0C482, accent: 0xC45F0A,
                  darkBackground: 0x32291F, darkKey: 0x55422E, darkFunctional: 0x6B5437, darkAccent: 0xFFA32B)
        case .penguin:
            .init(background: 0xDEDFDC, key: 0xFFFFF8, functional: 0xBFC2BF, accent: 0xEBAE32,
                  darkBackground: 0x262827, darkKey: 0x424643, darkFunctional: 0x555B56, darkAccent: 0xF4C458)
        case .fox:
            .init(background: 0xF0DAC8, key: 0xFFFAF1, functional: 0xE9B98F, accent: 0xBD531A,
                  darkBackground: 0x32251D, darkKey: 0x543E2E, darkFunctional: 0x6B4D36, darkAccent: 0xFFA159)
        case .panda:
            .init(background: 0xDEDEDC, key: 0xFCFCFA, functional: 0xC1C2C0, accent: 0x414442,
                  darkBackground: 0x242625, darkKey: 0x414543, darkFunctional: 0x535956, darkAccent: 0xD0D3CF)
        case .turtle:
            .init(background: 0xDFE6CE, key: 0xFCFDEC, functional: 0xC4CF9D, accent: 0x667628,
                  darkBackground: 0x282D1F, darkKey: 0x434B31, darkFunctional: 0x57613D, darkAccent: 0xC4D379)
        case .octopus:
            .init(background: 0xF0DAD7, key: 0xFFF8F4, functional: 0xEEB4AB, accent: 0xD6504F,
                  darkBackground: 0x332524, darkKey: 0x553B38, darkFunctional: 0x6B4B45, darkAccent: 0xFF9490)
        case .frog:
            .init(background: 0xE1E8C8, key: 0xFBFFE9, functional: 0xCAD995, accent: 0xAAC921,
                  darkBackground: 0x282F1E, darkKey: 0x444E30, darkFunctional: 0x58623B, darkAccent: 0xC1DE3A)
        case .chick:
            .init(background: 0xEFE6CD, key: 0xFFFCED, functional: 0xE7D49A, accent: 0xF1C232,
                  darkBackground: 0x302B1D, darkKey: 0x514832, darkFunctional: 0x665939, darkAccent: 0xF7D368)
        }
    }
}

private extension UIColor {
    static func themeColor(_ light: UInt32, _ dark: UInt32) -> UIColor {
        UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                           blue: CGFloat(hex & 255) / 255, alpha: 1)
        }
    }
    func withBrightness(_ factor: CGFloat) -> UIColor {
        UIColor { traits in
            var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            self.resolvedColor(with: traits).getHue(&h, saturation: &s, brightness: &b, alpha: &a)
            return UIColor(hue: h, saturation: s, brightness: b * factor, alpha: a)
        }
    }
}
