# iOS 自定义普通键盘与布局导入

开发版：0.1.0 (23)。这是本地开发交付，不代表 App Store 更新。

## 已实现范围

- 主 App「普通键盘布局与导入」：26 键经典、正交、分体模板；所有行的槽位数量、键位交换、键位库、功能键位置与宽度、键高、间距、分区、撤销/重做。
- 草稿与已应用快照分开；显式应用后，扩展在下一次唤出时读取。编辑器与扩展使用同一个 `CustomKeyboardLayout.geometry`。
- 新配置只写 App Group 的 `keyboard-layouts-v1.json`。不写原 `configuration-v1.json`，不改变方案选择修订或扩展的并击偏好。
- RIMES 原生 JSON、浏览器原型 JSON（完整独立字母布局）、仓鼠 YAML 和含 `hamster.yaml` 的 ZIP 导入；先检查和预览，再保存为新的草稿，另行应用。
- 主 App 才链接 Yams / ZIPFoundation；扩展不增加 YAML / ZIP 解析依赖。导入不执行 Lua，不安装词库或 Rime 方案，不覆盖全局设置。

## 用户样包

只读检查用户提供的「顶功592星猫键道(1).zip」，没有把词库或脚本加入仓库。152 项，7 个方案、25 个词典、78 个 Lua 文件。

转换出 `xingmao` 主布局，行槽数 `10,9,9,3`，共 31 键。原包缺少独立中英切换键，须先在编辑器补齐才能应用。70 项滑动、36 项长按菜单、2 个 characterMargin 边缘动作，以及独立横屏参数，均在兼容性说明中告知未保留。包内数字布局仍使用 RIMES 内置数字层；原数字布局一行横屏宽度合计 110% 的问题会报告。

星猫的顶功处理依赖 Lua 和方案部署，**本版仅导入键位，不代表星猫方案已可输入**。9/14/18 键共键解码也未接入。

## 兼容与验证

- `KeyboardGeometry.swift` 未修改；自定义路径按 `scheme != .chord` 隔离，包含并击内 EN、Shift、数字、表情子模式。
- 移动的是原有功能按钮实例，保留重复删除和空格光标操作；恢复默认/切回并击时按原顺序恢复底栏及约束。系统要求地球键时仍保留独立入口。
- 手机扩展偏好在安装前通过 devicectl 备份于忽略目录 `build/layout-native-baseline`：并击、正交、中文、强触感。原 App Group 根目录配置受 devicectl 的 Library/Documents/tmp 读取限制，不能在安装前直接复制；没有以电脑默认配置冒充手机备份。
- Shared 模型 13 项测试通过；新增原生运行时 4 项、存储 2 项、导入器 11 项全部通过，共 17 项。
- 首开高度回归 3 项通过；已有引擎/键盘/交互测试 61 项中 60 通过、1 项宣传截图测试按预期开关跳过。
- 导入器针对真实包中的 YAML 隐式 `=` 标量标签补充回归后，最终 11 项复测通过。独立原生用例合计 **80 项通过、1 项预期跳过**；Shared 的 13 项另计。
- Xcode 的测试结果为 `TEST SUCCEEDED`。测试结束后的诊断收集另报全局 CLT 找不到 simctl，不影响已完成的用例结果；执行命令始终指定 Xcode `DEVELOPER_DIR`。

## 实际模拟器界面验证

- 从系统文件选择器选择用户提供的星猫 ZIP，完成兼容性检查、候选预览、导入新草稿的完整流程；回读确认 `active == nil`，没有自动应用。
- 在编辑器中将 W 拖至 E：两槽内容交换为 E / W，槽位坐标保持原样。独立 UI dump 确认 `orthogonal.w` 的标签为 E、`orthogonal.e` 的标签为 W。
- 从键位库将 A 拖入目标槽位，原 A 槽位被清空；撤销后恢复原布局。
- 保存草稿后仍未应用。设置紧凑尺寸为键高 38 pt、间距 4 pt、边距 4 pt、分区间距 10 pt，再明确点击应用。
- 切换到全拼并重新唤起真实键盘扩展，实际显示交换后的 `QEW` 排列和紧凑尺寸。截图：[custom-keyboard.png](../build/layout-native-validation/custom-keyboard.png)。
- 在该自定义布局上逐键输入 `nihao`，候选出现「你好」；点击自定义空格键后，宿主输入框的实际值为「你好」。

上述拖放、导入与扩展显示结果均来自 **iOS 模拟器**，不是物理手机上的手感验证。

## 物理设备交付与待确认

- 开发版 **0.1.0 (23)** 已成功安装到连接的 iPhone 15 Pro。
- devicectl 安装结果及安装后 App 清单均确认版本 **0.1.0 (23)**；签名验证通过。
- 在手机主 App 内运行相同导入服务，成功将真实 ZIP 中的 `xingmao` 保存为未应用草稿。设备报告 `passed: true`、`savedDraft: xingmao`，并明确提示补齐中英切换键。报告保存在忽略目录 `build/layout-native-validation/device-import-smoke.json`。
- 安装前、安装后从手机回读的 `keyboard-preferences-v1` 解码后完整相等：`scheme: chord`、`chordLayout: orthogonal`、`englishInput: false`、`haptics: true`、`hapticStrength: strong` 均保留。原始序列化数据不同，比较依据为解码后的全部字段；plist 的其他条目也全部相等。比较报告位于忽略目录 `build/layout-native-validation/device-preferences-comparison.json`。
- 通过显式开发诊断保存了手机原 App Group 配置的原始副本，位于忽略目录 `build/layout-native-baseline/device-configuration-postinstall.json`。这是安装后的只读备份，不是安装前后文件一致性的证明。
- 最后重新正常启动手机主 App，清除本次导入诊断启动参数。
- 微信等真实宿主里的输入、拖动与键盘手感仍需人工确认；模拟器通过和设备安装成功不替代这些验证。
