# RIMES Brand Assets

`rimes-rhino.png` is the shared, transparent master for every current RIMES app: the seated gray rhino with an orange cap. Use this artwork for future builds; do not restore the former teal/star icon. The older SVG files are historical artwork and are not consumed by the generator.

Run `bash Logo/generate.sh` on macOS with Xcode installed. It exports and checks all platform assets in one pass; commit the generated files so Android, Windows and Linux builds do not need macOS or image generation.

- macOS: transparent ICNS, 16pt input-source PDF, and monochrome menu templates. The production input-menu PDF stays single-page.
- iOS: opaque 1024px app icon on neutral `#F7F5F1`, plus a transparent in-app brand image. System masking supplies the rounded corners.
- Android: 108dp adaptive foreground and monochrome layers, with the character filling the central 66dp safe area; neutral background in `colors.xml`. See [Android adaptive-icon requirements](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
- Windows: transparent multi-resolution ICO embedded in native executables, TSF and the installer.
- Linux: transparent hicolor icons from 16 to 512px, installed under the `rimes` icon name used by Fcitx and GTK.

Square exports use 92% of the canvas height, measured from the nontransparent artwork rather than the source canvas, preserving the cap, horn, feet and tail. Platform resizing and encoding are deterministic; `export-icons.swift` never regenerates the character.

The cleanup provenance and exact image-generation prompt are recorded in [rhino-provenance.md](rhino-provenance.md).
