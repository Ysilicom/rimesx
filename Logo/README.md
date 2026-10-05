# RIMES Brand Assets

`rimes-rhino.png` is the shared, transparent master for every current RIMES app: the seated gray rhino with an orange cap. Use this artwork for future app icons; do not restore the former teal/star icon. The former `app-icon.svg`, `rimes-mark.svg`, `inputsource.svg` and `menubar.svg` are historical artwork and are not consumed by the generator.

Small macOS input-source and menu symbols use a curved rhino horn with a transparent inner highlight. Its 16pt vector path lives in `export-icons.swift`, which exports `rhino-horn.svg`, PDF and PNG variants from the same geometry. Do not replace it with a silhouette of the full mascot.

Run `bash Logo/generate.sh` on macOS with Xcode installed. It exports and checks all platform assets in one pass; commit the generated files so Android, Windows and Linux builds do not need macOS or image generation.

- macOS: full-mascot transparent ICNS; vector horn for the 16pt input-source PDF and monochrome menu templates. The production input-menu PDF stays single-page so AppKit can tint it for light and dark appearances.
- iOS: opaque 1024px app icon on neutral `#F7F5F1`, plus a transparent in-app brand image. System masking supplies the rounded corners.
- Android: 108dp adaptive foreground and monochrome layers, with the character filling the central 66dp safe area; neutral background in `colors.xml`. See [Android adaptive-icon requirements](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
- Windows: transparent multi-resolution ICO embedded in native executables, TSF and the installer.
- Linux: transparent hicolor icons from 16 to 512px, installed under the `rimes` icon name used by Fcitx and GTK.

Square mascot exports use 92% of the canvas height, measured from the nontransparent artwork rather than the source canvas, preserving the cap, horn, feet and tail. Platform resizing and encoding are deterministic; `export-icons.swift` never regenerates the character.

The cleanup provenance and exact image-generation prompt are recorded in [rhino-provenance.md](rhino-provenance.md).
