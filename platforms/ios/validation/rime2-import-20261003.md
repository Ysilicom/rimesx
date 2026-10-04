# 小鹤双拼雾凇包兼容修复（2026-10-03）

本次只交付修复后的方案 ZIP 与导入说明，不修改 App 源码、不安装新 App、不启用或覆盖用户手机的现有方案。

源归档 SHA256：`3fd0d1e7de3806c780754cb37acee51bdccbb0035bd5f9ba890c754064f543db`。
交付归档：`/Users/isaac/Downloads/RIMES-小鹤双拼-修复版-20261003/rimes-flypy-20261003.zip`；SHA256：`29d1ccb9b913eaab3fa7eb105a7beec55bb3003b0a9ce3d3a8fab60a389281bd`。

## 原因与兼容处理

现有 `RimeSchemeImportService` 在发现 schema 时也计入 `__MACOSX/.../._double_pinyin_flypy.schema.yaml`，原包实际复现“压缩包存在多个方案根目录”。此外，资源白名单只允许根目录词典，`import_tables` 标识也不支持 `cn_dicts/`；检查器分别检查基础 schema 和补丁，导致已被补丁替代的 `luna_pinyin` / `stroke` 仍是缺失依赖。原包确实没有 `symbols.yaml`、日期 Lua 和个人短语源表。

修复包清理隐藏元数据与用户数据库，平铺六份原始词典并修正根词典引用；有效补丁合入 schema，保留编码规则、原启用词库、表情和自定义符号，补齐项目已有的标准符号及 OpenCC 资源。去除未提供脚本的日期组件；保留空自定义短语接口。说明中明确功能范围。

## 验证

- `build/rime2-repair-validation/import-check` 直接编译当前导入器源文件，原包复现失败，修复包检查/暂存成功，无方案阻断项。
- 各子词库文件与源包逐字节相同，原 ZIP SHA256 不变；最终 ZIP CRC 完整。
- 使用当前 `RimeMobile.mm` 和冻结引擎库：主机、iOS 26.5 arm64 模拟器各 8 项通过（你好、中国、世界、学习、苹果、分数、表情符号、退格）；原表情建议出现在“你好”等候选中。
- 模拟器执行的是独立原生引擎探针，包含无预编译缓存的部署、加载和输入检查；不是 App 图形导入流程或物理手机微信验证。
- 两次部署各写独立暂存目录；没有访问用户的真实学习数据库或激活选择。

完整检查报告、探针及修复脚本位于忽略目录 `platforms/ios/build/rime2-repair-validation/`。
用户说明：`/Users/isaac/Downloads/RIMES-小鹤双拼-修复版-20261003/导入步骤与修复说明.txt`。
