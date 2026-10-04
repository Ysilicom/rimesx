# Android keyboard icons

The keyboard uses an Android-owned subset of 26 icons from the official [Lucide repository](https://github.com/lucide-icons/lucide),
pinned to commit `12691c45680f5d088bbdccf301f268e6cf904682`.
`lucide.lock.json` records every upstream SVG URL and SHA-256, every generated
VectorDrawable SHA-256, and the complete license hash. The unmodified SVGs and
license are retained under `lucide/`.

All icons retain Lucide's 24 × 24 viewport, 2-unit stroke, round caps and round
joins. At the usual 20 dp size the stroke is approximately 1.67 dp. Android's
native VectorDrawable paints the current button tint in each enabled, pressed
and selected state; there is no runtime Lucide library, SVG parser or symbol font.
These are Lucide icons, and do not distribute Apple SF Symbols artwork.

The converter keeps path geometry, converts circles and rounded rectangles to
equivalent arcs, and separates SVG's compact arc flags for Android's PathParser.
`SHIFT_FILL` uses the official `arrow-big-up-dash` caps-lock marker. `BOOK` uses
the closed `book-text`; `SMILE` uses the current `face-slightly-smiling` name.
`WRITE` uses `square-pen` for the input experience entry.

From the repository root:

```sh
python3 platforms/android/resources/icons/convert-lucide.py --check
# Explicitly regenerate checked-in XMLs, packaged license and vector hashes:
python3 platforms/android/resources/icons/convert-lucide.py --write
```

The complete upstream license includes both ISC and the MIT notice for icons
derived from Feather. The same bytes are packaged as
`app/src/main/assets/licenses/Lucide-LICENSE.txt` with the keyboard APK. The
source SVG subset is for provenance and regeneration; the app uses the native
vectors in `app/src/main/res/drawable/`.
