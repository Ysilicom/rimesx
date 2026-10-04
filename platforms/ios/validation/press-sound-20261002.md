# iOS 原生皮肤按压反馈与默认键盘音效 — build 27

## 行为

- 原生皮肤的字母、9 键字母组和功能键按下时显示蓝底白字 / 白图标，松开后回到普通或选中状态。已经处于蓝色选中态的功能键按下时使用更深的色彩，仍能区分按压。经典皮肤继续使用原来的青绿色反馈。
- `keySounds` 默认开启。旧配置没有该字段时也开启；用户明确关闭后，编码保存与再次加载均保留关闭值。
- 键盘齿轮菜单上部直接提供「按键音效」开关，与触感开关独立。按键触发点击声，预览组合变化和候选提交不会为同一次输入额外播放第二声。
- 使用苹果公开的 `UIDevice.playInputClick()` 标准键盘点击声接口。现有 `UIInputView` 遵循 `UIInputViewAudioFeedback`，没有替换根输入视图、改动高度约束或设置应用音频会话。

苹果文档说明该接口播放系统键盘点击声，并遵循系统键盘声音开关。第三方键盘的音频能力属于完全访问范围；本次未更改用户的系统声音设置或权限。

来源：[playInputClick](https://developer.apple.com/documentation/uikit/uidevice/playinputclick())、[Custom Keyboard](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)。

## 验证

- Shared `KeyboardExperienceTests`：18 项通过，包含旧配置默认开启、显式关闭的持久化。
- iOS 输入交互、标准键盘、首次高度：65 项，64 通过、1 项主动跳过的宣传截图测试，无失败。新增验证覆盖关闭触感仍可点击发声、关闭声音不影响触感、并击预览 / 提交不重复发声，以及保留原根视图自适应高度。
- 测试结果：`Test-RIMES-2026.10.02_16-16-07-+0800.xcresult`。Xcode 在测试通过后的额外诊断收集阶段仍提示子进程找不到 `simctl`，不影响测试断言结果；实际 UI 采集单独指定 Xcode 环境。
- 最后的图标高亮与菜单位置调整经过模拟器 / 真机 Debug 重建；资源检查、差异空白检查与严格签名验证通过。

实际模拟器扩展（iPhone 17 Pro，iOS 26.5）：

- 浅色 26 键 Q：长按截图为蓝底白字，松开恢复白底黑字。
- 深色 9 键 ABC：长按截图为蓝底白字，松开恢复灰底白字。
- 删除键：长按蓝底白图标，松开恢复。关闭了原生皮肤下 UIButton 对高亮图标的额外变暗，避免白色图标被自动压成灰色。
- 「按键音效」初始勾选。实际点按关闭、退出再打开输入体验，菜单仍未勾选，扩展偏好 `keySounds == false`。验证后已在模拟器恢复开启。
- 上述音效测试验证调用与设置行为；没有把模拟器截图或注入的音频回调当作真机听感验证。

本地证据保存在忽略目录 `build/press-sound-validation/`，包括按压 / 松开截图、菜单 UI 树与 `sound-persistence.json`。

## 真机安装

已安装至 iPhone 15 Pro，安装后清单确认 **0.1.0 (27)**。回执数据库序号为 1816，应用容器为 `13364487-5B4B-4BC2-9CBB-5BBFFD5F9EEF`。

安装后手机扩展已经保存新增的 `keySounds: true`，原正交并击参数与导入方案清单保留。对比中只有原输入选择字段 `scheme` / `lastChineseScheme` 从 `pinyin` 变为 `chord`，因此不把整个偏好文件宣称为完全一致，也没有回写旧选择覆盖正在使用的手机状态。其余既有偏好值一致，方案清单完全一致。

安装 / 读回证据：`device-install27.json`、`device-apps27.json`、`phone-pre27-*`、`phone-post27-*`。真机扬声器的实际听感尚未独立验证。
