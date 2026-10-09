<h1 align="center">RIMES X</h1>

<p align="center">
  <b>Android 极客级高性能输入法、微信键盘质感交互与 120Hz 极限流水线（私有定制增强版）</b><br/>
  <b>Geek-Grade High-Performance Chinese IME, WeChat-Keyboard Aesthetics & 120Hz Pipeline for Android</b><br/>
  <sub>librime 1.17 · 微信键盘布局 · 小鹤双拼键帽助记 · 120Hz 零分配渲染 · 空间触控容错 · 中英混输 · 离线词库 · 云端 CI</sub>
</p>

<p align="center">
  <a href="#english"><b>English</b></a> &nbsp;|&nbsp; <a href="#-rimes-x-中文说明"><b>简体中文</b></a> &nbsp;|&nbsp; <a href="README.en.md">Full English Doc</a> &nbsp;|&nbsp; <a href="README_zh.md">完整中文文档</a>
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

---

<a id="english"></a>
## 📖 English

### Overview

**RIMES X** is a production-hardened, high-performance Chinese input method distribution tailored specifically for Android 8.0+ mobile devices. It is forked from the open-source project [scholay/rimes](https://github.com/scholay/rimes) (Lingxi IME) and powered by [RIME](https://rime.im/) (librime 1.17.0).

While upstream `scholay/rimes` focuses primarily on the macOS desktop ecosystem (with LaunchAgent guards, system-wide hotkeys, and floating desktop windows), **RIMES X pivots 100% to the Android mobile experience**.

RIMES X enhances the upstream codebase with **WeChat-Keyboard matte tactile aesthetics**, **native Xiaohe Shuangpin (Flypy) with visual keycap hints**, **Gboard-level 26-key spatial touch error correction**, **120Hz locked-framerate zero-allocation rendering**, **mixed Chinese-English candidate generation**, **symmetric 123/symbol pages with an adaptive punctuation weight row**, **context-aware field routing (email/password/URI)**, and an **automated GitHub Actions cloud CI pipeline with persistent local release re-signing**.

---

### 🌟 RIMES X Custom Highlights

#### 1. 🎨 WeChat-Keyboard Tactile Layout & Smart Ergonomics
- **Matte Borderless Keycap Aesthetic**: Re-engineered `KeyButton` drawing pipeline with geometrically balanced **Manrope** typography, ensuring crisp contrast and clear visual feedback for press and long-press states.
- **Smart 2-in-1 Combo Punctuation Key**: The comma key handles rapid double-tap (≤500ms) and long-press gestures: single tap inserts comma `，` (half-width `,` in English mode); double tap or long-press inserts period `。` (half-width `.` in English mode), drastically reducing thumb travel.
- **Dedicated Language Toggle & Widened Space**: Emoji key moved to the top utility row, expanding spacebar width by 35%. A dedicated language toggle sits directly adjacent to space for rapid one-handed switching between Chinese and English.
- **One-Tap Keyboard Dismissal Glyph**: A dedicated miniature keyboard glyph on the left of the candidate row allows immediate keyboard dismissal without relying on system back navigation gestures.
- **Stepless Height & Inset Adjustment**: Smooth keyboard height scaling via the gear panel, naturally adapting to compact phones, foldables, and tablets.

#### 2. 🕊️ Native Xiaohe Shuangpin (Flypy) & Visual Key Hints
- **Out-of-the-Box Flypy Scheme**: Bundles pre-tuned `rimes_flypy.schema.yaml` and dedicated dictionaries—no manual file copies or configuration required.
- **Permanent Visual Key Hints**: Keycaps clearly display initials and finals in subtle contrast, helping beginners touch-type immediately without memorizing key mappings.
- **Granular Fuzzy Pinyin**: Extends fuzzy pinyin matrix (z/zh, c/ch, s/sh, in/ing, en/eng, l/n, f/h) with individual toggles in settings.

#### 3. 🛡️ Gboard-Level Spatial Touch Correction & Gestures
- **Spatial Touch Correction (`SmartCorrector`)**: Captures exact coordinate displacement `(biasX, biasY)` relative to keycap center on touch down. Applies 2D Euclidean distance weighting to rescue dead-end inputs via 2-level neighbor prediction, ensuring high candidate accuracy even during rapid misclicks.
- **Swipe-Up Delete to Clear**: Single tap deletes one character; swiping upward on the backspace key clears the entire text field and uncommitted pinyin (or the entire Buffer draft).
- **Persistent Input Connection**: Seamlessly adopts replaced input connections across host application rebinds without dropping the session.

#### 4. ⚡ 120Hz Ultra-Smooth Pipeline & Zero-Jank Rendering
- **Zero-Allocation `onDraw` Architecture**: Re-architected `KeyboardSurface.onDraw()` path, stripping out per-frame transient objects (`FontMetrics`, `StringBuilder`), eliminating main-thread Android GC hitches and locking a continuous 120Hz refresh rate.
- **Decoupled State Pipeline**: Typing state updates are completely separated from key face rendering, delivering instantaneous touch responsiveness.
- **`.verified` State Cache**: Tracks verification states for bundled dictionary assets, bypassing heavy SHA-256 recalculation on every cold launch.

#### 5. 🔤 Context-Aware Fields, Mixed Candidates & Symmetrical Symbol Matrix
- **Context-Aware Field Routing**: Normal text fields display smooth English suggestions; visible-password, URI, email, and ASCII-only fields commit English directly. Email fields automatically present `@` and `.com` chips and restore the previous language state upon exit.
- **Symmetric 123 & Symbol Matrix**: The symbol page aligns perfectly with the 123 numeric page (two rows of 10 keys with 6 marks between functional keys).
- **Adaptive Punctuation Weight Row**: When idle, the candidate row displays 10 high-frequency marks sized to digit key width, sharing unified metrics across Chinese and English. Enriched with ellipsis (`……`), dash (`——`), middle dot (`·`), and book quotes (`《》`).
- **Mixed Chinese-English Lexicon**: Intelligently interleaves English words (via `melt_eng`) and modern tech terms ("帧数" / FPS, "掉帧" / frame drop, "显存" / VRAM, "算力" / compute), with expandable 54-item grid browsing.

#### 6. 📋 Explicit Staging Buffer & 100% Offline Privacy
- **Explicit Staging Buffer**: Confirmed text can remain staged on the keyboard workbench before block delivery or full insertion.
- **100% Offline Privacy**: Pinyin engine, lexicons, and offline translation run exclusively within the local Android sandbox. Zero network telemetry, zero background uploads of keystrokes, clipboard, or passwords.
- **Optional Private AI Assistant**: Online AI is completely disabled by default. If desired, configure your own custom HTTPS endpoint and API key in settings, protected in hardware via **Android Keystore**.

#### 7. 🚀 Production Release R8 Optimization & Automated Cloud CI Workflow
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

### 🌐 Full Feature Matrix (At a Glance)

- **Input Schemes**: Xiaohe Shuangpin (built-in with hints), Full Pinyin, Natural Code, Wubi 86, and English (`melt_eng`).
- **Keyboard Layouts**: 26-key QWERTY, 9-key Pinyin keypad, symbol matrix, and emoji picker.
- **Staging Buffer**: Explicit buffer before committing text, supporting block-by-block insertion and draft staging.
- **Productivity Tools**: Visual clipboard history, offline dictionary translation, and optional Android Keystore-secured private AI.
- **Security & Privacy**: 100% on-device execution, zero third-party telemetry, zero network dependencies.

---

### 🛠️ Build & Workflow

#### 1. Cloud Build & Automatic Re-Signing (GitHub Actions CI)
Trigger or attach to GitHub Actions CI and re-sign with local permanent keys:
```bash
# Automated trigger, 3-minute progress monitoring, artifact download & local signing
./scripts/cloud-build-and-sign.sh

# Or attach to an in-progress run ID directly:
./scripts/cloud-build-and-sign.sh <RUN_ID>
```

#### 2. Manual Re-Signing
Sign any downloaded release APK using the persistent keystore:
```bash
./platforms/android/scripts/sign-rimesx.sh path/to/rimesx-release.apk
```
Certificate verification fingerprint:
```text
Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
```

#### 3. Strict No-Local-Compilation Rule
To conserve local CPU/memory resources, local binary compilations (`build-engine.py`, `assembleRelease`, `ninja`, `cmake`) are disabled. All builds run on GitHub Actions.

---

### 🔄 Upstream Sync Protocol

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

### 📄 License & Credits

- Forked from [scholay/rimes](https://github.com/scholay/rimes) (Lingxi IME).
- Powered by [RIME (Rime Input Method Engine)](https://rime.im/) (librime 1.17.0).
- Licensed under the **[Apache-2.0](LICENSE)**.

---

<a id="-rimes-x-中文说明"></a>
## 🇨🇳 RIMES X 中文说明

### 📖 项目概述

**RIMES X** 是专为 Android 8.0+ 打造的极客级高性能输入法、微信键盘质感交互与 120Hz 极限流水线增强版。本项目基于开源 [scholay/rimes](https://github.com/scholay/rimes)（灵犀输入法）深度定制，底层由著名的 [RIME](https://rime.im/)（librime 1.17.0）核心驱动。

上游源库 `scholay/rimes` 的设计重心主要偏向 macOS 桌面端生态（涵盖 LaunchAgent 守卫、跨输入法全局快捷键、桌面端窗口化 Capsule/Mailbox、多平台预览等机制）。**RIMES X 则彻底聚焦于 Android 移动端打字体验**。

RIMES X 在剥离桌面端冗余代码的同时，专属引入了 **微信键盘风格磨砂无边框质感**、**小鹤双拼原生集成与键帽助记符号**、**Gboard 级 26 键空间几何触控纠偏**、**120Hz 零分配满帧渲染流水线**、**中英混输智能候选**、**123/符号页对称对齐与自适应标点权重栏**、**智能输入框场景路由（邮箱/密码/URI）**，以及 **GitHub Actions CI 云端 R8 极致优化编译 + 本地永久私钥一键重签名** 的工业级自动化流水线。

---

### 🌟 RIMES X 核心定制特性

#### 1. 🎨 微信键盘质感交互与智能人体工学布局
- **磨砂无边框键帽设计**：重构 `KeyButton` 绘制流程，采用几何优化的 **Manrope** 优雅字体，保持字母完全不透明与高对比度，按键视觉沉浸高级。
- **逗号/句号智能二合一极速键**：26 键模式下单按输入逗号 `，`（英文半角 `,`），快速双击（≤500ms）或长按即输入句号 `。`（英文半角 `.`），大幅减少切换标点界面的多余手指位移。
- **独立中英切换键与拓宽空格**：Emoji 键上移至顶栏功能区，底部空格宽度增加 35%，空格右侧设立常驻独立中英切换键，单手操作也能瞬时切换输入状态。
- **候选栏独立键盘收起按钮**：候选栏最左侧常驻独立微缩键盘图形按键，无需依赖系统返回手势即可一键收起键盘。
- **无级高度缩放与底栏边距**：齿轮外观面板支持键盘高度自适应平滑微调与底部避让区调节，完美适配小屏、折叠屏及平板大屏。

#### 2. 🕊️ 小鹤双拼原生集成与直观键帽助记 (Key Hints)
- **开箱即用方案集成**：代码库内置 `rimes_flypy.schema.yaml` 及专属配套词库，无需任何手动文件拷贝或外部配置导入。
- **键帽视觉助记 (Key Hints)**：在 26 键键帽右上角以适度灰度精确排布对应声母与韵母助记符号，初学双拼轻松盲打，熟手输入行云流水。
- **细粒度双拼模糊音**：扩展自然码与小鹤双拼模糊音矩阵（z/zh、c/ch、s/sh、in/ing、en/eng、l/n、f/h 等），支持在设置中按需单独开启。

#### 3. 🛡️ Gboard 级空间几何触控容错 (`SmartCorrector`) 与手势操作
- **空间触控纠偏模型 (`SmartCorrector`)**：在触控阶段捕获触点相对键帽中心的偏移向量 `(biasX, biasY)`，引入二维欧氏空间距离加权算法，针对高频死胡同输入启动两级邻键救援预测，快速盲打误触也能精准预测正确候选词。
- **退格键上滑一键清空输入框**：点按退格键正常删除单个字符；在退格键上向上滑动即可一次性清空输入框文本及未确认拼音（开启 Buffer 时清空整段暂存草稿）。
- **宿主重绑定连接持久化**：应用重绑定时平滑接管 InputConnection 避免会话丢失，中英切换键增加状态保持重试机制。

#### 4. ⚡ 120Hz 极限高刷与零卡顿渲染管线 (Zero-Allocation onDraw)
- **零分配渲染架构 (Zero-Allocation onDraw)**：彻底重构 `KeyboardSurface.onDraw()` 渲染路径，剔除频繁瞬态对象分配与主线程 GC 垃圾回收抖动，按键动画与拖拽在 120Hz 高刷屏下满帧丝滑。
- **打字更新解耦**：打字状态变更与键帽绘制完全解耦，触控响应时间缩短至毫秒级。
- **`.verified` 字典校验缓存机制**：记录离线词典校验状态，跳过冷启动重复执行的大文件 SHA-256 哈希重算，实现键盘秒起秒开。

#### 5. 🔤 智能场景路由、对称符号矩阵与中英混合词库
- **智能场景路由识别**：普通文本显示流畅英文候选建议；明文密码、URI、邮箱及纯 ASCII 字段直接敲击即上屏英文。邮箱输入框专享 `@` 与 `.com` 快捷胶囊，退出后自动恢复语言状态。
- **123 与符号键盘结构对称对齐**：符号页面与数字页面完全对齐（两行各 10 个按键，侧键之间排布 6 个标点）。
- **自适应高频标点推荐栏**：未组字时，候选栏自动呈现为 10 个与数字按键等宽展示的高频标点权重推荐栏，中英文共用指标；补齐省略号（`……`）、破折号（`——`）、间隔号（`·`）和书名号（`《》`）。
- **中英混输现代词库**：集成 `melt_eng` 表支持拼音中英混合输入，增补现代科技/极客/游戏热词（“帧数”、“掉帧”、“显存”、“算力” 等），支持 54 项大网格候选浏览。

#### 6. 📋 显式缓冲工作台 (Buffer) 与纯本地离线隐私
- **显式缓冲工作台 (Buffer)**：确认文字可先暂存于键盘缓冲区，支持分块投递或一键发送整段草稿。
- **纯本地离线隐私**：拼音算法、离线词库、离线翻译完全在手机本地沙箱运行，零网络权限依赖，绝不静默收集或上传任何用户击键、剪贴板与密码信息。
- **可选个人 AI 助手**：在线 AI 默认完全关闭；若需使用快问、润色或作诗，可在设置中填入个人自建或第三方大模型 HTTPS 接口与密钥，私钥由系统级 **Android Keystore** 硬件级安全存储。

#### 7. 🚀 生产级 Release R8 极致压缩与全自动云端构建/签名流
- **严格遵循本地免编译规范 (No Local Compilation)**：本地设备 CPU/内存专注于代码编辑与 git 操作。C++ 核心交叉编译（ARM64/x86_64）与 R8 混淆瘦身全量交由 GitHub Actions 云端完成。
- **一键全自动云构建与签名脚本 (`scripts/cloud-build-and-sign.sh`)**：
  - 自动触发或接入 CI 构建并以 3 分钟间隔定时轮询状态。
  - 免 Token 通过 `nightly.link` 直链自动拉取制品，无视 GitHub API 速率限制。
  - 自动解压并通过本地永久私钥库（`~/.config/rimesx/rimesx-release.jks` / `platforms/android/scripts/sign-rimesx.sh`）完成签名，保障永久无缝覆盖升级（`adb install -r`）：
    ```text
    Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
    ```

---

### ⚔️ 相对源库 (scholay/rimes) 核心改动全景对比

| 维度 | 源库 (`scholay/rimes`) | RIMES X (本仓库) | 改进意义与体验提升 |
| :--- | :--- | :--- | :--- |
| **平台聚焦** | 主攻 macOS 桌面端，包含复杂的 LaunchAgent 守护与桌面窗口 | **全面聚焦 Android 移动端**，精简移除桌面无关逻辑与文档噪音 | 专为移动端小屏/折叠屏调优，轻量专注 |
| **视觉质感** | 基础 Android IME 界面，按键样式与排版较简陋 | **微信键盘风格磨砂无边框质感**，Manrope 优雅字体与高对比度按键 | 视觉沉浸高级，明暗对比清晰，长时间打字不易疲劳 |
| **小鹤双拼** | 需用户自行手动导入配置，键帽无提示 | **原生内置小鹤双拼方案**，键帽右上角常驻**声母/韵母助记符 (Key Hints)** | 新手盲打无需背键位，开箱即用行云流水 |
| **标点按键** | 传统逗号/句号分离或频繁切换符号页 | **逗号/句号智能二合一极速键**（单击 `，`，双击或长按 `。`） | 极大缩减大拇指位移，标点输入一气呵成 |
| **符号键盘** | 符号页面与数字页面网格割裂散乱 | **符号页与 123 数字页对称对齐** + **10 键自适应标点权重栏** | 统一数字与符号空间记忆，常用标点一点即达 |
| **场景路由** | 面对密码、URL、邮箱等特殊输入框无差别对待 | **智能场景路由**：密码/URI 直出英文，邮箱专享 `@` / `.com` 胶囊并自动恢复状态 | 特殊输入框输入体验行云流水 |
| **键盘收起** | 仅依赖系统底部手势或返回键 | **候选栏常驻独立微缩键盘收起图标** | 单击一键收起，避免误触返回操作 |
| **中英切换** | 依赖符号页切换或长按空格切换 | **空格右侧独立中英切换键**，底栏空格同步大幅拓宽 35% | 单手盲打快速切中英文，敲击更从容 |
| **触控容错** | 传统绝对几何命中，边缘误触率高 | **Gboard 级 26 键空间几何距离权重容错模型 (`SmartCorrector`)** | 快速盲打时自动纠正邻键误触，首选命中率大幅提升 |
| **手势操作** | 仅有点按与长按删除单个字符 | **退格键上滑一键清空输入框 (Swipe-up Delete to Clear)** | 快速重写场景下一滑即清，效率倍增 |
| **120Hz 高刷** | 绘图主线程存在频繁对象分配与 GC 抖动卡顿 | **零分配渲染架构 (Zero-Allocation onDraw)**，打字与绘制解耦 | 消除垃圾回收引起的微卡顿，锁定 120Hz 满帧触控 |
| **冷启动性能** | 每次启动均对离线词典执行全盘 SHA-256 哈希计算 | **`.verified` 字典校验缓存机制**，跳过重复全盘哈希 | 键盘秒起秒开，杜绝点击输入框时的启动停顿 |
| **候选词生态** | 仅支持基础中文候选，翻页数量有限 | **中英混输智能候选**（集成 `melt_eng`）、增补现代科技热词、**54 项大网格候选浏览** | 中英文免切混输，词库更贴合当下科技语境 |
| **候选交互** | 点击候选词偶尔发生偏移或错选 | **重构候选词直接触控映射**，修正 librime 候选词索引对齐 | 保证“所见即所点”，点击候选词精准上屏 |
| **剪贴板管理** | 清空剪贴板后易发生恢复异常或读取失效 | **基于 Application Context 稳定监听**，配套可视化独立历史面板 | 剪贴板历史随用随粘，生命周期管理更健全 |
| **键盘高度** | 仅有粗粒度档位，调节容易引起字体拉伸扭曲 | **无级平滑高度调节与底栏防误触边距**，矢量字体自适应居中 | 完美契合全面屏手势条、折叠屏与各尺寸平板 |
| **打包与签名** | 依赖本地重度编译环境，自签名易引起更新冲突 | **云端 Actions 免本地算力打包 + 本地永久 Keystore 签名体系** | 本地无需配置耗电编译链，覆盖安装（`adb install -r`）永不丢个人词库 |

---

## 🌐 核心功能特性一览

- **输入方案支持**：小鹤双拼（原生集成带助记）、雾凇全拼、自然码、五笔 86、英文（`melt_eng` 词库）。
- **键盘布局形态**：26 键全键盘、九宫格拼音键盘、独立符号矩阵键盘、Emoji 表情面板。
- **缓冲工作台 (Buffer)**：显式暂存待发文本，支持分块投递或整段草稿一键插入。
- **效率工具箱**：独立可视化剪贴板历史面板、离线英汉词典翻译、Android Keystore 级安全私有大模型助手。
- **安全与隐私**：100% 本地沙箱执行，零第三方遥测，无静默联网权限。

---

## 🛠️ 构建与工作流 (Build & Workflow)

### 1. 云端构建与一键自动重签名 (GitHub Actions CI)
自动化触发云端全量 R8 编译、3 分钟定时状态监控与本地签名：
```bash
# 全流程：触发提交 -> 3 分钟轮询构建状态 -> 自动下载 -> 本地私钥签名
./scripts/cloud-build-and-sign.sh

# 或直接接入正在进行的云端任务：
./scripts/cloud-build-and-sign.sh <RUN_ID>
```

### 2. 本地私钥重签名
从 Actions 下载构建产物后，使用本地专用签名脚本进行重签名：
```bash
./platforms/android/scripts/sign-rimesx.sh path/to/rimesx-release.apk
```
签名验证证书指纹：
```text
Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
```

### 3. 严格遵循本地免编译规范 (No Local Compilation)
为了保护移动开发机 CPU/内存资源，请勿在本地运行 `build-engine.py` 或 `./gradlew assembleRelease`。所有 Native 二进制与 Release APK 打包全部托管给 GitHub Actions 云端跑道。

---

### 🔄 上游代码同步指引 (Upstream Sync Protocol)

RIMES X 定期跟踪并合并上游 `scholay/rimes` 的最新演进：
```bash
# 1. 配置上游远程仓库
git remote add upstream https://github.com/scholay/rimes.git

# 2. 获取上游最新分支代码
git fetch upstream main

# 3. 合并上游更改至 main
git checkout main
git merge upstream/main

# 4. 解决冲突并保留 RIMES X 核心定制代码
# 5. 推送至 Fork 仓库
git push origin main
```

---

### 📄 协议与致谢 (License & Credits)

- Fork 自 [scholay/rimes](https://github.com/scholay/rimes)（灵犀输入法）。
- 中文输入逻辑基于 [RIME (中州韵输入法引擎)](https://rime.im/)（librime 1.17.0）。
- 遵循 **[Apache-2.0](LICENSE)** 开源协议。
