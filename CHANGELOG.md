# 更新日志

本文件由 `python3 scripts/release/release_tool.py changelog --write` 根据公开 GitHub Release 与
[Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/) 生成，请勿手工编辑。
每个版本的安装方式与校验和见 [GitHub Releases](https://github.com/scholay/rimes/releases)。

## [v1.1.0](https://github.com/scholay/rimes/releases/tag/v1.1.0) — 2026-10-04

### 新功能

- **mobile:** localize branding and add website and email links (a641efc)
- **mobile:** refine settings hero and complete iOS typing follow-up (e8dff04)
- **windows:** integrate official plugins and chording lifecycle (06ad59c)
- **plugins:** migrate official sources and mobile package lifecycle (cf84aa5)
- **plugins:** pin official sources and verify package execution (e022135)
- **windows:** native 1.0.0 version identity, Setup EXE and installer (dbcf63d)
- **android:** network AI transport, release build and 1.0.0 identity (80b1875)
- **ios:** custom layouts, imported schemes, nine-key and stats card (3c65b38)
- **mailbox:** add plugin-hosted messages and terminal workspaces (b878580)
- **android:** restore grouped app settings and keyboard configuration (d14a0e6)
- **android:** execute offline translation and OpenAI format mock plugins (bba5842)
- **android:** bundle verified offline Chinese English dictionary (d858fab)
- **android:** bind plugin results to captured Buffer sources (08af767)
- **windows:** align native UI with macOS themes and geometry (efec80f)
- **android:** align keyboard chrome and add two-thumb input (bd8cafd)
- **android:** port iOS touch chords and key geometry (5881b8f)
- **android:** adopt iOS-style keyboard layouts and palettes (889d420)
- **android:** add offline nine-key pinyin resources (9b4ef87)
- **windows:** add native Buffer workbench and daily preview pipeline (f884ece)
- **windows:** stage complete native shared data with integrity checks (f23d4c9)
- **android:** preserve Chinese Buffer blocks and control local learning (70412f5)
- **android:** add Chinese composition and stable candidate keyboard (eff9ca7)
- **android:** bundle pinned offline Rime engine and mobile dictionaries (11bccdc)
- **windows:** stage complete native shared data with integrity checks (1a62790)
- **android:** add native keyboard and target-bound Buffer foundation (5a23996)
- **windows:** add TSF composition, candidate window, and IME e2e (beaf105)
- **ios:** prepare RIMES 0.1.0 for review (9aea6f3)

### 修复

- **ios:** remove typing-path I/O and prioritize key haptics (5c654eb)
- **windows:** allow explicit reinstall after plugin receipt corruption (26635fc)
- **windows:** check bounded lock acquisition result explicitly (6ad0785)
- resolve packaged resources and bounded Windows input contention (2c1d97a)
- **plugins:** read source notices as UTF-8 on Windows (7c9d461)
- **windows:** split embedded packages within MSVC literal limits (fae05d7)
- **plugins:** enforce module contributions and isolate optional feature smoke (b0afe86)
- **ci:** include mailbox pages in settings render checks (5daa62b)
- **windows:** isolate typing tests from workbench settings (454b237)
- **ci:** repair platform build prerequisites (2d520b4)
- **android:** use a writing icon for the typing entry (ea8a281)
- **windows:** republish Buffer target after selection changes (48c435b)
- **windows:** restore explicit Buffer capture after focus changes (be788f7)
- **windows:** refine macOS control surfaces and settings interactions (6ec5233)
- **android:** retire native key gestures across input boundaries (58f7395)
- **android:** restore idle Buffer plugins and unify vector icons (5953b96)
- **android:** reflect Buffer and host action key states (58527b9)
- **windows:** preserve candidate state across unhandled key releases (348822f)
- **windows:** run candidate clicks in a granted TSF edit session (4ea3e1c)
- **windows:** repair native host candidate and composition behavior (b5a66ee)
- **windows:** translate completed sentences and stop empty-result retries (7c33090)
- **windows:** preserve learned databases during deployment and audit installer recovery (f722071)
- **windows:** verify registration outside TSF caches and revoke stale broker deliveries (201bc5d)
- **android:** apply learning changes to the active input session (70e6301)
- **windows:** permit Session 0 identities in broker hello messages (689e84d)
- **android:** keep long candidate phrases readable on phone screens (236546d)
- **android:** keep keyboard controls clear of system insets (55e2ec7)
- **windows:** permit Session 0 identities in broker hello messages (1b2a86a)
- **android:** honor editor actions and clear unbound Buffer targets (e89a5f2)
- **windows:** keep composition across unhandled IME KeyUp (8653bef)
- **windows:** supply ascii_composer bindings for the IME e2e schema (4b3db54)
- **windows:** compile Fake TSF on MSVC and keep Windows CI off the macOS gate (8ef9bec)

### 性能

- **android:** reuse immutable light and dark palettes (f2311f4)

### 文档

- **release:** record final iOS upload and correct TestFlight availability (eeb2899)
- **release:** update platform downloads and source checkout guidance (69fcf3d)
- **release:** clarify background input validation boundary (76e0407)
- **release:** finalize 1.1.0 delivery acceptance (bb73f45)
- **windows:** record 1.0.0 native release (4abd532)
- **android:** record 1.0.0 release and dev8 validation (941111c)
- **release:** record 1.0.0 publication and readiness (6fecd95)
- **ios:** record 1.0 validation, privacy and release notes (d12280e)
- **readme:** refresh READMEs and add ja/ko/es translations (e57e8c7)
- **license:** relicense to Apache-2.0 and record attribution (133374f)
- **android:** record dev7 settings delivery and validation (0099461)
- **android:** record dev6 local plugins and verified delivery (f2ecb2f)
- **windows:** record Buffer binding repair acceptance on Young (ab92666)
- **windows:** record second styling round on Young (4511eff)
- **android:** record dev5 parity and physical acceptance (f020344)
- **windows:** record installed macOS style parity acceptance (92de58f)
- **windows:** record real-host preview.4 acceptance and Cursor handoff (e74a6f1)
- **windows:** record preview artifacts and remaining desktop acceptance gates (f8ba5c3)
- **android:** record physical device validation and dev 2 artifacts (1c2b2ee)
- define platform priorities and record native foundation validation (38953e4)

### 维护

- **release:** record physical acceptance and documented distribution basis (d886b02)
- pin official plugins with four-platform native CI (f9ec959)
- **macos:** distinguish pinyin segmentation and isolate fixture sizing (e02d4b7)
- **windows:** extract verified engine archives with bundled tar (1b7f513)
- **ios:** verify shared storage across signed app and keyboard (73c5829)
- **windows:** wait for context binding before typing assertions (5cdae3d)
- **release:** unify product versions and platform release tooling (186c2a6)
- ignore Shared and Xcode build artifacts (8077a8b)
- **android:** measure host latency and exercise chord lifecycles (6f4b932)
- **windows:** verify unavailable Broker input and reconnect state (995751f)
- **android:** record dev 4 keyboard layout delivery (27878b1)
- **windows:** allow candidate guard checks in headless Broker (c99a3aa)
- **windows:** use a word present in the TSF fixture (4abf14e)
- **android:** record dev 3 engine and physical-device acceptance (818b02a)
- standardize mainline maintenance and independent platform channels (5b72a4d)
- **promo:** preserve local iOS promotional source and assets (946fbe3)
- **design:** preserve local interactive workbench updates (61bead5)
- **ios:** preserve local keyboard and AI workspace changes (b013051)
- **macos:** preserve local Buffer and Capsule development (d460f2a)
- **core:** preserve local shared models and plugin catalog (ef74b97)

## [v1.0.0](https://github.com/scholay/rimes/releases/tag/v1.0.0) — 2026-10-03

### 合并的 PR

- #42
- #41
- #39
- #38
- #37
- #35
- #33
- #32 fix(release): report the notary submission id and bound the wait
- #31
- #30
- #29
- #28 fix(release): handle localized diagnostics on macOS Bash
- #27 fix(release): stage formal installer before publishing
- #26 feat: manage multiple AI provider profiles
- #25
- #24 docs: README 末尾加入微信赞助二维码
- #23 让隐私声明与架构文档回到实现现状
- #22 修复 Xcode 27 默认构建系统下的入口点

### 新功能

- **linux:** port Capsule note rail to Fcitx5 (fa1f895)
- **linux:** port Default Buffer workbench to Fcitx5 (ffe320e)
- **linux:** add a Fcitx5 RIMES input method addon (b6f1227)
- **capture:** add ratio frame workflow and clean selection backgrounds (e464255)
- **ios:** tune keyboard interactions from physical feedback (d3f58ad)
- **ios:** deliver keyboard and Default Buffer experience through build 22 (68edcea)
- **ui:** refine capsule cards and adaptive candidate layout (a879d05)
- **capsule:** simplify secure card workflows and rounded chrome (ab62b42)
- **capture:** refine screenshot UI, interactions, and permissions (02ee106)
- **ios:** add native RIMES keyboard development preview (ad97986)
- **capture:** unify capture permissions and rework the editor chrome (20651da)
- manage multiple AI provider profiles (e7fa86c)

### 修复

- **linux:** copy Capsule notes on Wayland via wl-copy (46fa869)
- **linux:** copy Capsule notes only when copy_seq advances (40787c6)
- **linux:** make Capsule double-click insert without rebuilding cards (d0077fb)
- **linux:** clear Capsule search on hide and flag password fields (91f11a0)
- **linux:** end Buffer drag on pointer release, not a settle timer (62e93f8)
- **linux:** treat non-drag same-IC reactivation as a field switch (809853d)
- **linux:** keep a single Buffer UI and treat same-IC caret moves as field switches (81516e4)
- **linux:** keep Buffer capture across X11 drags and place above caret (7f7c447)
- **linux:** stop Buffer Enter/ZWSP leaks and respawn the panel (5bd60d1)
- **linux:** make Buffer e2e drive real Fcitx5 capture (e253249)
- **linux:** cover rebuild without a second librime setup() (9a31346)
- **linux:** notify Fcitx5 when background deploy finishes (98f9e5c)
- **linux:** keep Fcitx5 responsive during first-run deploy (1b813cb)
- **candidates:** close the panel as soon as text commits (7fcbb55)
- **ios:** protect proxy writes from host callback reentrancy (e144549)
- **capture:** disambiguate recorder geometry on CI toolchains (aac216b)
- **capture:** restore protected windows and preserve edit history (38084b1)
- **ios:** configure device signing and verify first iPhone install (2c06e07)
- **capture:** repair the result overlay, card geometry and search focus (ba1fcb8)
- **build:** verify one architecture per lipo call (4932385)
- **release:** report the notary submission id and bound the wait (90238f3)
- **release:** make macOS the sole release gate (950c568)
- **release:** notarize only the formal installer (aedc5f1)
- **release:** register and validate the signing keychain (fa71f2e)
- **release:** delimit variables in localized shell diagnostics (0f2a874)
- **release:** finish GUI rehearsal when the receipt is committed (7fc516f)
- **release:** allow scholay to approve both release stages (892895e)
- **release:** stage formal installer before publishing (90c566c)
- **build:** drop the dead standalone smoke entry points (42341c3)

### 文档

- refresh multilingual README and language badges (#47) (54701c9)
- thank iRime for guidance and promotion (f27a3fb)
- **release:** separate tag creation from immutability (75b4ded)
- **release:** clarify one reviewer can approve both stages (2dea9de)
- reconcile README and issue baselines (92c7e53)
- **readme:** add the WeChat sponsor QR code (08b1433)
- state the real paste, identity, and Capsule behaviour (2a87e78)
- update CHANGELOG.md for v0.5.0-preview.3 (25865bd)

### 维护

- **linux:** keep Buffer drag tail inside 1s under Capsule load (4105ea1)
- **linux:** keep Buffer drag e2e timers alive after the suite returns (3a2ce3c)
- **linux:** drain leftover Buffer chips between e2e scenarios (1a29f8e)
- **linux:** drop the staged raw-input chip before later Buffer e2e steps (ab64133)
- **linux:** wait for SIGKILL before asserting UI pid is gone (b6b866a)
- **ios:** await plugin events instead of CI timing assumptions (95d6f00)
- **ios:** validate release tooling without credentials (5b6fb7c)
- **ios:** automate tagged App Store releases (43692ba)
- **capture:** check in the capture module as delivered (df5a901)
- **release:** align CI with explicit maintainer approvals (f22c77c)

## [v0.5.0-preview.3](https://github.com/scholay/rimes/releases/tag/v0.5.0-preview.3) — 2026-09-15

### 合并的 PR

- #17 内置呦呦音形折梅、寒梅原生并击方案
- #21 更新 CHANGELOG（v0.5.0-preview.2）

### 新功能

- **chord:** native chord schemes that settle on key release (0930901)
- **rime-data:** bundle 呦呦音形 折梅 and 寒梅 chord schemes (e6ace2c)

### 文档

- update CHANGELOG.md for v0.5.0-preview.2 (a9957f0)

### 维护

- run the chord and 呦呦音形 smokes in CI and on the assembled app (a805ad9)

## [v0.5.0-preview.2](https://github.com/scholay/rimes/releases/tag/v0.5.0-preview.2) — 2026-09-15

### 合并的 PR

- #20 不再跟踪 Python 字节码缓存
- #19 统一发布流程——一条命令、tag 为唯一版本来源
- #18 发布前演练 v0.5.0-preview.1 升级，修复 universal 构建
- #16 打包校验改为 55 个文件的审核闭包
- #15 Buffer 输入框锁定、Capsule、自然码并击与 RIMES 更名

### 新功能

- **release:** one release command that only pushes a tag (560c224)
- **release:** derive versions, notes, and changelog from tags (12a06d5)
- **chord:** 自然码 output encoding for chord keymaps (2ba4edf)
- **capsule:** default the Capsule rail to ⌘⇧V and finish the rename (5e50330)
- **capsule:** one Capsule with the manager drawn as the rail grown upward (c79aa54)
- **capsule:** save Recent cards into Capsule from the rail (d8fcf36)
- **capsule:** turn the clipboard rail into Capsule with saved-entry tabs (34790cc)
- **buffer:** lock sending to the exact input box and rework the toolbar (ef632a0)
- **settings:** keep only the main title on every settings page (ff773cd)
- **buffer:** fold the rails whenever the workbench cannot take a keystroke (1b562fc)
- **buffer:** fold the rails to the toolbar when the workbench has no focus (6b7cf4f)
- **buffer:** add the composing field so any input method can type here (7009d49)
- **buffer:** name the two input modes and fence them at the boundary (a0fcba1)
- **buffer:** show live typing figures as a second line in Default mode (6b5a089)
- **doctor:** report the app's own identity, signing and permission state (f972a8a)
- **migration:** one definition of where data lives, and a proven way to move it (4440ef7)
- **permissions:** clear the record so the system will ask again, and name  the identity it asks about (7a7a92e)
- **settings:** audit system permissions item by item, and stop asking for  grants the system will not prompt for (143c6e2)
- **clipboard:** make activation a choice, and say when a paste was blocked (48bbc1a)
- **buffer:** put closing-after-the-last-block in the auto-send menu (4bc3f5a)
- **codex:** follow a real session instead of reading one field of it (6564b5f)
- **aggregator:** route a published model catalog by endpoint, not by name (cd374ba)
- **buffer:** add AudioKit music plugin and fix continuous plugin cycling (a627226)
- **stream-input:** compose in the raw line and age blocks out (6a1d66b)
- **statistics:** add graphical dashboards and article typing tests (f5d6151)
- **chord:** add configurable keymaps and unify split strokes (fc8f8a6)
- **buffer:** source-row import, split pull-downs, tighter geometry (fb71e17)
- **stream-input:** pause segments, comma types, models are selectable (dc9cc40)
- add workbench menu, toast, auto-paste, and input-box probe (bd701a2)
- visualize Buffer target association (94a1ee0)
- harden standalone companion workflows (182c19a)
- copy Capsule media and harden model fetch (dd43816)
- add secure Capsule iCloud sync (eba5185)
- keep the Buffer toolbar permanently visible (46846b1)
- complete standalone workspaces and rich clipboard (ce69283)
- add local-first Mailbox and Capsule workflows (ba61146)

### 修复

- **release:** build CHANGELOG.md from the tags origin publishes (5119ddd)
- **release:** build each architecture natively and merge with lipo (0e09fd5)
- **platform-preview:** pin the 55-file reviewed closure (4750235)
- finish moving code paths to ~/Library/RIMES (51a8f11)
- keep tool output and a real home path out of logs and fixtures (fd6ee49)
- **buffer:** restore the target link after leaving Music (36460a5)
- **buffer:** lock Electron apps and custom-drawn apps to their input box (25f2964)
- **buffer:** fold the whole panel, hold it steady, and recall the lost target (d07e397)
- **buffer:** give the standalone rail a click that can succeed, and stop  reparenting during layout (209c18e)
- **buffer:** stop a focus activation from undoing the capture it was given (be7600d)
- **buffer:** stop the metrics row from collapsing every rail (6db2e52)
- **release:** finish the rename in packaging, and sign so grants survive (d392690)
- **buffer:** hand Return back when nothing is staged, and stop hiding options (011eb85)
- **buffer:** keep a capture click that has nowhere to land yet (80f2059)
- **buffer:** isolate translation units and correct auto-send lifecycle (93072bd)
- **input:** let a Shift tap switch language, and stop swallowing letters (ef3c2b7)
- **buffer:** align the caret opening to the first character, not the edge (1a7d565)
- **menu:** dispatch maintenance commands; surface the Accessibility grant (4ef66fb)
- **clipboard:** trackpad direction, clicks hold the rail, copy feedback (49c2e44)
- preserve Chinese mode after utility shortcuts (e0fb5f9)

### 重构

- one name — RIMES — for the bundle, executable, identifier and data (d3a5620)
- **translation:** extract the session bridge and give codex a working PATH (be77bd6)
- **stream-input:** guess on the connector alone (a30d773)
- **capsule:** free-form password secrets; retire Prompt/Memory/URL (43d6de7)
- float Buffer rail actions (2d2c361)

### 文档

- **release:** one-page process, with reference and history split out (9350038)
- point data paths at ~/Library/RIMES (260c5fa)
- **chord:** point the keymap directory at ~/Library/RIMES (0f33412)
- **chord:** document the 自然码 output encoding (3996d18)

### 维护

- stop tracking Python bytecode caches (ba8e0f6)
- **release:** publish previews from tags, rehearse packaging on PRs and nightly (cb69fd9)
- **release:** rehearse the upgrade from v0.5.0-preview.1, and finish the rename in docs (cdcfd34)
- wait for the companion launch agent instead of racing it (e8c4cbb)
- **capsule:** wait for passcode sheets instead of one 50ms turn (d9ed303)
- **buffer:** let the Music panel smoke run under a foreign input method (dd76bd5)
- bring the smokes and settings render count up to this branch (61544ff)
- pin the RIMES input-source identity (a34e7f6)
- **chord:** add the Isaac2025 and Isaac2026 keymaps (582dbbf)

### 其他

- Keep capture across the session the toolbar click itself destroys (ba32b62)
- Shrink the panel to a toolbar strip when the rails fold (9670ce2)

## [v0.5.0-preview.1](https://github.com/scholay/rimes/releases/tag/v0.5.0-preview.1) — 2026-08-22

### 新功能

- prepare RIMES 0.5 input and buffer release (96ae5dd)
- **ui:** port React design system to native surfaces (09cfe78)
- **design:** slim input menu and rethink buffer rail layouts (7706571)
- **design:** add interactive React design system (9053a90)
- **settings:** add quiet theme and configurable shortcuts (db67a00)
- **windows:** add native TSF foundation (4c20c39)

### 修复

- **settings:** refresh choice card visual states (ae170fc)
- repair settings choices and clipboard shortcut (a8add72)
- **ui:** repair settings and compact buffer layout (bb400a9)
- **design:** restore compact buffer actions (c419b06)
- **design:** harden buffer and inbox interactions (9779409)
- **design:** align semantic colors with native UI (6893277)
- show candidates in iShot annotations (089b4f1)
- **windows:** avoid PowerShell automatic variable collision (2209b09)

### 文档

- establish community contribution workflows (3ee4cf0)
- formalize AI contributor attribution (47a68d9)
- credit Claude, Cursor, Codex, and Grok as contributors (0438492)

### 维护

- render default settings surface deterministically (bf2446b)
- **windows:** pin Visual Studio 2022 runner (cfcd830)

### 其他

- enable controlled unsigned previews (08917f7)

## [v0.4.3](https://github.com/scholay/rimes/releases/tag/v0.4.3) — 2026-08-15

### 修复

- harden RIMES installation, updater, and release (ce63692)

## [v0.4.2](https://github.com/scholay/rimes/releases/tag/v0.4.2) — 2026-08-10

### 新功能

- add Windows and Linux input scheme previews (4acf8ca)
- add marine chrome integration and polish buffer UI (7762417)
- add buffer plugin cycling and improve candidate contrast (e1b5f1f)
- broaden CLI compatibility and polish workbench UI (a3309f9)
- add My Prompt search plugin (3006c34)
- add local OCR for Remarkable (da81d4b)
- add configurable plugins and remarkable import (fb87fa8)
- support multiple enabled buffer plugins (4589191)
- promote prepared actions to the workbench primary control (526311d)
- refine workbench editing and stream input (e401ccc)
- ship RIMES workbench and stream input (347f90d)
- unify AI connectors and support context-only generation (f5991fc)
- **输入法:** 重构设置并扩展缓冲插件平台 (851345f)
- **缓冲工作台:** 简化交互并加固焦点与主题 (bf15df2)
- **缓冲工作台:** 独立窗口与焦点安全投递 (9576f3a)
- **候选窗+MCP:** 尺寸约束+实时预览, MCP 2025-06-18, 通用接入 (87db0b3)
- **M2:** local gateway + MCP + inbound bus + inbox (verified end-to-end) (6e376cc)
- **ui:** workbench settings IA + three-layer panel preview (56b43ef)
- **M1:** block provenance (Origin) + echo guard + source badges (91826f7)
- buffer workbench groundwork + M0 safety baseline (41583c4)

### 修复

- verify universal release binary correctly (9dbc258)
- centralize releases in scholay/rimes (8fa9fce)
- support macOS save panels and literal v input (3a6d381)
- stabilize focused workbench and marine chrome capture (76cff6d)
- harden plugin configuration storage and layout (15d5a2b)
- support stream chords and three-row layouts (addfc66)
- harden buffer paste and stream alternatives (0aff412)
- restore Control-Space input switching (1dbdbf9)
- unify workbench capture and semantic blocks (b41cbae)
- harden workbench delivery and Shift handling (ae251d8)
- **ime:** restore candidate paging and literal keys (7fe7c82)
- restore local CLI authentication (dcfc30f)
- **缓冲工作台:** 收紧单行界面并彻底隔离 Enter 回调 (7670621)
- **候选窗+MCP:** 补齐预览约束并加固本地网关 (001c015)

### 文档

- add MIT license (68c716a)
- add bilingual READMEs with product demo links (fdbe0d7)
- note WeChat input-switch crash as a known macOS 26 limitation (e785801)

### 维护

- normalize Rime data line endings (d61d534)
- harden preview and prompt smoke timing (e2aef88)
- point release updates at scholay/rimes (baaf2ae)
- 隔离 AI 缓冲插件夹具 (870b49e)
- 隔离并标注冒烟测试 (e0d98b2)
- bump version to 0.4.0 (804276b)
- bump version to 0.3.1 (0e9cb42)
- bump version to 0.3.0 (2f368de)
- run remote-smoke in CI (552bad9)
- bump version to 0.2.0 (d3b55b7)

### 其他

- Refine input schemes, buffer UI, and compatibility (df5b509)
- Improve input source registration and installer flow (2f63d6f)
- Add option-based character selection (111a146)
- Refine Enter input method UI (154ab5f)
- Refine candidate window UI (584b6b4)
- Add Marine buffer bridge and harden ETInput metadata (f688466)
- v0.4.1: restore schema-switch menu, import user Rime config, tidy input source (8740440)
- Bundle 雾凇/串击/并击 Rime schemas for a truly self-contained build (d6d9f9c)
- v0.3.8: fix input-source enable/icons on macOS 26 + move menu into system input menu (661a6b2)
- Rebrand to 恩特输入法 + new icon set (app, status-bar, input-source) (419db50)
- Fix "no candidates": dynamic schema menu + guard stale preference; PDF icon (2719e20)
- Add input-mode menu icon (fixes blank input source row + enable failure) (448c74e)
- Fix duplicate/blank input source: give the input mode a distinct id (2321f6d)
- Show localized name 恩特输入法 in input source list + fix installer dark-mode text (be6c78f)
- Add guided .pkg installer for first-time install (568a217)
- Fix repeated Keychain password prompt for remote-typing identity (c90969c)
- Add 隔空传字 (Mac-to-Mac remote typing) over encrypted LAN P2P (f4738f4)
- Self-contained librime + rebrand to 恩特输入法 (ETInput) + logo (0131ff6)
- Add GitHub Actions CI and in-app auto-update (7cd7404)
- Improve buffer mode controls and release feedback (548ddb0)
- Refine candidate buffer UI and controls (b4e9c74)
- Keep buffer anchored with candidate window (141fa91)
- Add keyboard frequency heatmap (1d8bd55)
- Refine RimeBuffer candidate and buffer UI (3b63bc4)
- Initial RimeBuffer import (a26da80)
