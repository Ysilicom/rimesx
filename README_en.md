<h1 align="center">RIMES X</h1>

<p align="center">
  <b>Geek-Grade High-Performance Chinese IME, WeChat-Keyboard Aesthetics & 120Hz Pipeline for Android</b><br/>
  <sub>librime 1.17 · WeChat-Keyboard Layout · Xiaohe Flypy Key Hints · 120Hz Zero-Allocation Rendering · Spatial Touch Correction · Mixed English-Chinese · Offline Lexicon · Cloud CI</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Edition-RIMES%20X%20Private-brightgreen?style=flat-square" alt="Edition" />
  <img src="https://img.shields.io/badge/Package-org.scholay.rimes.android-9cf?style=flat-square" alt="Package" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Engine-librime_1.17.0-007AFF?style=flat-square" alt="librime" />
  <img src="https://img.shields.io/badge/Upstream-scholay%2Frimes%20Synced-blue?style=flat-square" alt="Upstream" />
  <img src="https://img.shields.io/badge/Arch-arm64--v8a%20%7C%20x86__64-purple?style=flat-square" alt="Arch" />
  <img src="https://img.shields.io/badge/License-Apache--2.0-orange?style=flat-square" alt="License" />
</p>

<p align="center">
  <a href="README.md"><b>Bilingual Overview</b></a> &nbsp;|&nbsp; <b>English</b> &nbsp;|&nbsp; <a href="README_zh.md"><b>简体中文</b></a>
</p>

---

## 📖 Overview

**RIMES X** is a production-hardened, high-performance Chinese input method distribution tailored specifically for Android 8.0+ mobile devices. It is forked from the open-source project [scholay/rimes](https://github.com/scholay/rimes) (Lingxi IME) and powered by [RIME](https://rime.im/) (librime 1.17.0).

While upstream `scholay/rimes` focuses primarily on the macOS desktop ecosystem (with LaunchAgent guards, system-wide hotkeys, and floating desktop windows), **RIMES X pivots 100% to the Android mobile experience**.

RIMES X enhances the upstream codebase with **WeChat-Keyboard matte tactile aesthetics**, **native Xiaohe Shuangpin (Flypy) with visual keycap hints**, **Gboard-level 26-key spatial touch error correction**, **120Hz locked-framerate zero-allocation rendering**, **mixed Chinese-English candidate generation**, **symmetric 123/symbol pages with an adaptive punctuation weight row**, **context-aware field routing (email/password/URI)**, and an **automated GitHub Actions cloud CI pipeline with persistent local release re-signing**.

---

## 🌟 RIMES X Custom Highlights

### 1. 🎨 WeChat-Keyboard Tactile Layout & Smart Ergonomics
- **Matte Borderless Keycap Aesthetic**: Re-engineered `KeyButton` drawing pipeline with geometrically balanced **Manrope** typography, ensuring crisp contrast and clear visual feedback for press and long-press states.
- **Smart 2-in-1 Combo Punctuation Key**: The comma key handles rapid double-tap (≤500ms) and long-press gestures: single tap inserts comma `，` (half-width `,` in English mode); double tap or long-press inserts period `。` (half-width `.` in English mode), drastically reducing thumb travel.
- **Dedicated Language Toggle & Widened Space**: Emoji key moved to the top utility row, expanding spacebar width by 35%. A dedicated language toggle sits directly adjacent to space for rapid one-handed switching between Chinese and English.
- **One-Tap Keyboard Dismissal Glyph**: A dedicated miniature keyboard glyph on the left of the candidate row allows immediate keyboard dismissal without relying on system back navigation gestures.
- **Stepless Height & Inset Adjustment**: Smooth keyboard height scaling via the gear panel, naturally adapting to compact phones, foldables, and tablets.

### 2. 🕊️ Native Xiaohe Shuangpin (Flypy) & Visual Key Hints
- **Out-of-the-Box Flypy Scheme**: Bundles pre-tuned `rimes_flypy.schema.yaml` and dedicated dictionaries—no manual file copies or configuration required.
- **Permanent Visual Key Hints**: Keycaps clearly display initials and finals in subtle contrast, helping beginners touch-type immediately without memorizing key mappings.
- **Granular Fuzzy Pinyin**: Extends fuzzy pinyin matrix (z/zh, c/ch, s/sh, in/ing, en/eng, l/n, f/h) with individual toggles in settings.

### 3. 🛡️ Gboard-Level Spatial Touch Correction & Gestures
- **Spatial Touch Correction (`SmartCorrector`)**: Captures exact coordinate displacement `(biasX, biasY)` relative to keycap center on touch down. Applies 2D Euclidean distance weighting to rescue dead-end inputs via 2-level neighbor prediction, ensuring high candidate accuracy even during rapid misclicks.
- **Swipe-Up Delete to Clear**: Single tap deletes one character; swiping upward on the backspace key clears the entire text field and uncommitted pinyin (or the entire Buffer draft).
- **Persistent Input Connection**: Seamlessly adopts replaced input connections across host application rebinds without dropping the session.

### 4. ⚡ 120Hz Ultra-Smooth Pipeline & Zero-Jank Rendering
- **Zero-Allocation `onDraw` Architecture**: Re-architected `KeyboardSurface.onDraw()` path, stripping out per-frame transient objects (`FontMetrics`, `StringBuilder`), eliminating main-thread Android GC hitches and locking a continuous 120Hz refresh rate.
- **Decoupled State Pipeline**: Typing state updates are completely separated from key face rendering, delivering instantaneous touch responsiveness.
- **`.verified` State Cache**: Tracks verification states for bundled dictionary assets, bypassing heavy SHA-256 recalculation on every cold launch.

### 5. 🔤 Context-Aware Fields, Mixed Candidates & Symmetrical Symbol Matrix
- **Context-Aware Field Routing**: Normal text fields display smooth English suggestions; visible-password, URI, email, and ASCII-only fields commit English directly. Email fields automatically present `@` and `.com` chips and restore the previous language state upon exit.
- **Symmetric 123 & Symbol Matrix**: The symbol page aligns perfectly with the 123 numeric page (two rows of 10 keys with 6 marks between functional keys).
- **Adaptive Punctuation Weight Row**: When idle, the candidate row displays 10 high-frequency marks sized to digit key width, sharing unified metrics across Chinese and English. Enriched with ellipsis (`……`), dash (`——`), middle dot (`·`), and book quotes (`《》`).
- **Mixed Chinese-English Lexicon**: Intelligently interleaves English words (via `melt_eng`) and modern tech terms ("帧数" / FPS, "掉帧" / frame drop, "显存" / VRAM, "算力" / compute), with expandable 54-item grid browsing.

### 6. 📋 Explicit Staging Buffer & 100% Offline Privacy
- **Explicit Staging Buffer**: Confirmed text can remain staged on the keyboard workbench before block delivery or full insertion.
- **100% Offline Privacy**: Pinyin engine, lexicons, and offline translation run exclusively within the local Android sandbox. Zero network telemetry, zero background uploads of keystrokes, clipboard, or passwords.
- **Optional Private AI Assistant**: Online AI is completely disabled by default. If desired, configure your own custom HTTPS endpoint and API key in settings, protected in hardware via **Android Keystore**.

### 7. 🚀 Production Release R8 Optimization & Automated Cloud CI Workflow
- **Strict No-Local-Compilation Rule**: Local device CPU/memory is strictly reserved for code editing and git operations. Heavy native C++ (ARM64/x86_64) compilation and R8 optimizations run on GitHub Actions cloud runners.
- **One-Click Automated Cloud Build & Sign Script (`scripts/cloud-build-and-sign.sh`)**:
  - Automatically triggers or attaches to GitHub Actions CI runs and tracks execution with rate-limit protection.
  - Automatically pulls release artifacts via `nightly.link` without requiring API tokens.
  - Automatically verifies and re-signs APK using persistent local keystore (`~/.config/rimesx/rimesx-release.jks` / `scripts/sign-rimesx.sh`) with consistent SHA-256 signature for seamless updates (`adb install -r`):
    ```text
    Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
    ```

---

### ⚔️ Upstream Comparison Matrix: `scholay/rimes` vs `RIMES X`

| Dimension | Upstream (`scholay/rimes`) | RIMES X (This Repository) | Practical Impact & Experience |
| :--- | :--- | :--- | :--- |
| **Platform Focus** | Primarily macOS desktop with heavy background launch guards | **100% focused on Android mobile devices**; desktop clutter removed | Lightweight, specialized for phones, foldables, and tablets |
| **Visual Aesthetics** | Basic Android IME layout with utilitarian styling | **WeChat-Keyboard matte borderless aesthetics**, Manrope typography | Refined visual immersion, crisp high-contrast legibility |
| **Xiaohe Shuangpin** | Requires manual scheme import, blank keycaps | **Built-in out-of-the-box Flypy scheme**, permanent **Key Hints** on keycaps | Beginners touch-type without memorizing key mappings |
| **Punctuation Key** | Separate keys or frequent navigation to symbol pages | **Smart 2-in-1 combo comma/period key** (tap `,`, double-tap/long-press `.`) | Drastically reduces thumb travel during sentence composition |
| **Symbol/Number Layout**| Asymmetric symbol pages with disparate grids | **Symmetric 123 & symbol pages** + **10-mark adaptive weight row** | Muscle memory carries across numeric and symbol inputs |
| **Language Toggle** | Hidden behind symbol page or spacebar long-press | **Dedicated Chinese/English key right of spacebar**; widened spacebar | Instantaneous one-handed language toggling |
| **Field Adaptation** | Generic handling across all input fields | **Context-aware routing**: direct English for URI/passwords, email `@`/`.com` chips | Fluid specialized input experience in browsers and forms |
| **Keyboard Dismiss** | Relies entirely on system back navigation | **Dedicated miniature keyboard hide glyph** on candidate bar | Fast, intentional one-tap keyboard dismissal |
| **Touch Correction** | Strict geometric bounding box; frequent edge misclicks | **Gboard-level 26-key 2D spatial distance weighting model (`SmartCorrector`)** | Intelligently rescues dead-end words from neighboring misclicks |
| **Gesture Controls** | Single tap / long-press delete only | **Swipe-up on delete to clear entire field (or Buffer)** | Instantly resets text input field with a single swipe gesture |
| **120Hz High-Refresh** | Heavy object allocation in `onDraw`, GC jank | **Zero-Allocation `onDraw` architecture**, decoupled rendering | Eliminates garbage collection pauses, locking a smooth 120 FPS |
| **Cold Start** | Computes full SHA-256 hash on large dictionaries every launch | **`.verified` dictionary state cache mechanism** | Keyboard pops up instantly without cold-start hitch |
| **Lexicon & Candidates** | Standard Chinese candidates, limited pagination | **Mixed Chinese-English candidates** (`melt_eng`), modern tech terms, **54-item grid** | Smooth mixed-language typing without switching keyboards |
| **Candidate Precision** | Occasional index drift when tapping candidate items | **Rebuilt candidate touch mapping**, aligned librime indexing | Exact pixel-accurate tap-to-commit fidelity |
| **Clipboard History** | Stale resurrection bugs, unmanaged lifecycle | **Application-context listener**, dedicated visual clipboard panel | Reliable paste history with click-to-commit support |
| **Height & Insets** | Coarse step adjustments, font stretching on scale | **Stepless height scaling with bottom safety inset adjustment** | Adapts seamlessly to navigation bars, foldables, and tablets |
| **CI/CD & Signing** | Heavy local toolchain, ad-hoc certificates cause update errors | **GitHub Actions Cloud CI + Permanent Local Keystore Re-signing** | Zero local compute load; seamless in-place updates (`adb install -r`) |

---

## 🌐 Full Feature Matrix (At a Glance)

- **Input Schemes**: Xiaohe Shuangpin (built-in with hints), Full Pinyin, Natural Code, Wubi 86, and English (`melt_eng`).
- **Keyboard Layouts**: 26-key QWERTY, 9-key Pinyin keypad, symbol matrix, and emoji picker.
- **Staging Buffer**: Explicit buffer before committing text, supporting block-by-block insertion and draft staging.
- **Productivity Tools**: Visual clipboard history, offline dictionary translation, and optional Android Keystore-secured private AI.
- **Security & Privacy**: 100% on-device execution, zero third-party telemetry, zero network dependencies.

---

## 🛠️ Build & Workflow

### 1. Cloud Build & Automatic Re-Signing (GitHub Actions CI)
Trigger or attach to GitHub Actions CI and re-sign with local permanent keys:
```bash
# Automated trigger, 3-minute progress monitoring, artifact download & local signing
./scripts/cloud-build-and-sign.sh

# Or attach to an in-progress run ID directly:
./scripts/cloud-build-and-sign.sh <RUN_ID>
```

### 2. Manual Re-Signing
Sign any downloaded release APK using the persistent keystore:
```bash
./platforms/android/scripts/sign-rimesx.sh path/to/rimesx-release.apk
```
Certificate verification fingerprint:
```text
Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
```

### 3. Strict No-Local-Compilation Rule
To conserve local CPU/memory resources, local binary compilations (`build-engine.py`, `assembleRelease`, `ninja`, `cmake`) are disabled. All builds run on GitHub Actions.

---

## 🔄 Upstream Sync Protocol

RIMES X tracks and merges upstream changes regularly:
```bash
# 1. Ensure upstream remote is configured
git remote add upstream https://github.com/scholay/rimes.git

# 2. Fetch latest commits
git fetch upstream main

# 3. Merge latest upstream into main
git checkout main
git merge upstream/main

# 4. Resolve conflicts while preserving RIMES X custom optimizations
# 5. Push to the fork repository
git push origin main
```

---

## 📄 License & Credits

- Forked from [scholay/rimes](https://github.com/scholay/rimes) (Lingxi IME).
- Powered by [RIME (Rime Input Method Engine)](https://rime.im/) (librime 1.17.0).
- Licensed under the **[Apache-2.0](LICENSE)**.
