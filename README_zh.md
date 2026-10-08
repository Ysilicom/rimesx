<h1 align="center">RIMES X</h1>

<p align="center">
  <b>Android 极客级高性能输入法、微信键盘质感交互与 120Hz 极限流水线（私有定制增强版）</b><br/>
  <sub>librime 1.17 · 微信键盘布局 · 小鹤双拼键帽助记 · 120Hz 零分配渲染 · 空间触控容错 · 中英混输 · 离线词库 · 云端 CI</sub>
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
  <a href="README.md"><b>双语总览 (Bilingual)</b></a> &nbsp;|&nbsp; <a href="README.en.md"><b>English</b></a> &nbsp;|&nbsp; <b>简体中文</b>
</p>

---

## 📖 项目概述

**RIMES X** 是专为 Android 8.0+ 打造的极客级高性能输入法、微信键盘质感交互与 120Hz 极限流水线增强版。本项目基于开源 [scholay/rimes](https://github.com/scholay/rimes)（灵犀输入法）深度定制，底层由著名的 [RIME](https://rime.im/)（librime 1.17.0）核心驱动。

上游源库 `scholay/rimes` 的设计重心主要偏向 macOS 桌面端生态（涵盖 LaunchAgent 守卫、跨输入法全局快捷键、桌面端窗口化 Capsule/Mailbox、多平台预览等机制）。**RIMES X 则彻底聚焦于 Android 移动端打字体验**。

RIMES X 在剥离桌面端冗余代码的同时，专属引入了 **微信键盘风格磨砂无边框质感**、**小鹤双拼原生集成与键帽助记符号**、**Gboard 级 26 键空间几何触控纠偏**、**120Hz 零分配满帧渲染流水线**、**中英混输智能候选**，以及 **GitHub Actions CI 云端 R8 极致优化编译 + 本地永久私钥一键重签名** 的工业级自动化流水线。

---

## 🌟 RIMES X 核心定制特性

### 1. 🎨 微信键盘质感交互与智能人体工学布局
- **磨砂无边框键帽设计**：重构 `KeyButton` 绘制流程，采用几何优化的 **Manrope** 优雅字体，保持字母完全不透明与高对比度，按键视觉沉浸高级。
- **逗号/句号智能二合一极速键**：26 键模式下单按输入逗号 `，`（英文半角 `,`），快速双击（≤500ms）或长按即输入句号 `。`（英文半角 `.`），大幅减少切换标点界面的多余手指位移。
- **独立中英切换键与拓宽空格**：Emoji 键上移至顶栏功能区，底部空格宽度增加 35%，空格右侧设立常驻独立中英切换键，单手操作也能瞬时切换输入状态。
- **无级高度缩放与底栏边距**：齿轮外观面板支持键盘高度自适应平滑微调与底部避让区调节，完美适配小屏、折叠屏及平板大屏。

### 2. 🕊️ 小鹤双拼原生集成与直观键帽助记 (Key Hints)
- **开箱即用方案集成**：代码库内置 `rimes_flypy.schema.yaml` 及专属配套词库，无需任何手动文件拷贝或外部配置导入。
- **键帽视觉助记 (Key Hints)**：在 26 键键帽右上角以适度灰度精确排布对应声母与韵母助记符号，初学双拼轻松盲打，熟手输入行云流水。
- **细粒度双拼模糊音**：扩展自然码与小鹤双拼模糊音矩阵（z/zh、c/ch、s/sh、in/ing、en/eng、l/n、f/h 等），支持在设置中按需单独开启。

### 3. 🛡️ Gboard 级空间几何触控容错 (`SmartCorrector`) 与手势操作
- **空间触控纠偏模型 (`SmartCorrector`)**：在触控阶段捕获触点相对键帽中心的偏移向量 `(biasX, biasY)`，引入二维欧氏空间距离加权算法，针对高频死胡同输入启动两级邻键救援预测，快速盲打误触也能精准预测正确候选词。
- **退格键上滑一键清空输入框**：点按退格键正常删除单个字符；在退格键上向上滑动即可一次性清空输入框文本及未确认拼音（开启 Buffer 时清空整段暂存草稿）。

### 4. ⚡ 120Hz 极限高刷与零卡顿渲染管线 (Zero-Allocation onDraw)
- **零分配渲染架构 (Zero-Allocation onDraw)**：彻底重构 `KeyboardSurface.onDraw()` 渲染路径，剔除频繁瞬态对象分配与主线程 GC 垃圾回收抖动，按键动画与拖拽在 120Hz 高刷屏下满帧丝滑。
- **打字更新解耦**：打字状态变更与键帽绘制完全解耦，触控响应时间缩短至毫秒级。
- **`.verified` 字典校验缓存机制**：记录离线词典校验状态，跳过冷启动重复执行的大文件 SHA-256 哈希重算，实现键盘秒起秒开。

### 5. 🔤 智能候选与现代混合词库 (中英混输 / melt_eng)
- **中英混输智能候选**：拼音输入时底层智能混编匹配高频中英文混合词与英文词汇（集成 `melt_eng` 表），无需频繁切换中英键盘状态。
- **现代高频词库扩充**：收录当下前沿科技、数码、极客及游戏常用热词（如 “帧数”、“掉帧”、“显存”、“算力” 等）。
- **宽幅候选浏览与直触上屏**：支持展开多达 54 个候选词大网格浏览，首位候选保障中文优先级，修复 librime 索引对齐，点击候选词精准直达上屏。

### 6. 📋 显式缓冲工作台 (Buffer) 与纯本地离线隐私
- **显式缓冲工作台 (Buffer)**：确认文字可先暂存于键盘缓冲区，支持分块投递或一键发送整段草稿。
- **纯本地离线隐私**：拼音算法、离线词库、离线翻译完全在手机本地沙箱运行，零网络权限依赖，绝不静默收集或上传任何用户击键、剪贴板与密码信息。
- **可选个人 AI 助手**：在线 AI 默认完全关闭；若需使用快问、润色或作诗，可在设置中填入个人自建或第三方大模型 HTTPS 接口与密钥，私钥由系统级 **Android Keystore** 硬件级安全存储。

### 7. 🚀 生产级 Release R8 极致压缩与全自动云端构建/签名流
- **严格遵循本地免编译规范 (No Local Compilation)**：本地设备 CPU/内存专注于代码编辑与 git 操作。C++ 核心交叉编译（ARM64/x86_64）与 R8 混淆瘦身全量交由 GitHub Actions 云端完成。
- **一键全自动云构建与签名脚本 (`scripts/cloud-build-and-sign.sh`)**：
  - 自动触发或接入 CI 构建并以 3 分钟间隔定时轮询状态。
  - 免 Token 通过 `nightly.link` 直链自动拉取制品，无视 GitHub API 速率限制。
  - 自动解压并通过本地永久私钥库（`~/.config/rimesx/rimesx-release.jks` / `platforms/android/scripts/sign-rimesx.sh`）完成签名，保障永久无缝覆盖升级（`adb install -r`）：
    ```text
    Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
    ```

---

## ⚔️ 相对源库 (scholay/rimes) 核心改动全景对比

| 维度 | 源库 (`scholay/rimes`) | RIMES X (本仓库) | 改进意义与体验提升 |
| :--- | :--- | :--- | :--- |
| **平台聚焦** | 主攻 macOS 桌面端，包含复杂的 LaunchAgent 守护与桌面窗口 | **全面聚焦 Android 移动端**，精简移除桌面无关逻辑与文档噪音 | 专为移动端小屏/折叠屏调优，轻量专注 |
| **视觉质感** | 基础 Android IME 界面，按键样式与排版较简陋 | **微信键盘风格磨砂无边框质感**，Manrope 优雅字体与高对比度按键 | 视觉沉浸高级，明暗对比清晰，长时间打字不易疲劳 |
| **小鹤双拼** | 需用户自行手动导入配置，键帽无提示 | **原生内置小鹤双拼方案**，键帽右上角常驻**声母/韵母助记符 (Key Hints)** | 新手盲打无需背键位，开箱即用行云流水 |
| **标点按键** | 传统逗号/句号分离或频繁切换符号页 | **逗号/句号智能二合一极速键**（单击 `，`，双击或长按 `。`） | 极大缩减大拇指位移，标点输入一气呵成 |
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
# 赋予执行权限并对 APK 签名
./platforms/android/scripts/sign-rimesx.sh path/to/rimesx-release.apk
```
签名验证证书指纹：
```text
Signer #1 certificate SHA-256: fa2dc93cbd65c046230e611e0639b7d8245728e0e94fcf80b0a89bec4090272a
```

### 3. 严格遵循本地免编译规范 (No Local Compilation)
为了保护移动开发机 CPU/内存资源，请勿在本地运行 `build-engine.py` 或 `./gradlew assembleRelease`。所有 Native 二进制与 Release APK 打包全部托管给 GitHub Actions 云端跑道。

---

## 🔄 上游代码同步指引 (Upstream Sync Protocol)

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

## 📄 协议与致谢 (License & Credits)

- Fork 自 [scholay/rimes](https://github.com/scholay/rimes)（灵犀输入法）。
- 中文输入逻辑基于 [RIME (中州韵输入法引擎)](https://rime.im/)（librime 1.17.0）。
- 遵循 **[Apache-2.0](LICENSE)** 开源协议。
