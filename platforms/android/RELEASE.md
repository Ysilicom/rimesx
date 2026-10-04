# Android 1.1.0 发布准备

产品版本由 `VERSION` 提供，当前为 **1.1.0**，`versionCode=11`。每次对外提供新构建继续递增 code，不改变产品版本。正式身份为 `org.scholay.rimes.android`、名称 RIMES；开发包仍为 `.debug`、名称 RIMES Dev / RIMES 开发版。

## 本地构建

普通 `:app:assembleRelease :app:bundleRelease` 在没有签名材料时生成未签名产物，仅供检查。正式签名由以下四个环境变量提供，不能写进源码或命令参数：

- `RIMES_ANDROID_KEYSTORE`：长期保管的正式 keystore 的绝对路径。
- `RIMES_ANDROID_STORE_PASSWORD`、`RIMES_ANDROID_KEY_ALIAS`、`RIMES_ANDROID_KEY_PASSWORD`。

配置完成后执行 `bash scripts/build-release.sh`。脚本运行核心测试、Release lint、APK/AAB 构建、APK 签名验证及 16 KB 对齐验证，在 `dist/1.1.0/` 保存产物与 SHA256SUMS。没有签名时直接停止，不回退到 debug keystore，不安装或上传。

## 当前交付证据

- 1.0.0 已公开。1.1.0 code 11 已使用同一长期生产密钥签名，签名、对齐、核心测试和 Release lint 均通过；公开证书 SHA-256 为 `e6228d5b065f1f8c2f1e1162b80ea1a9245ce59a091bb6e403e47936c3820c54`。
- code 10 的正式包已在小米真机验收拼音上屏、密码/私密字段隔离及独立 API 地址、模型与密钥入口。code 11 仅更新设置首页 Hero 和构建号，保留数据升级成功，版本元数据与 Hero 实际界面均已核对。
- DeepSeek 的真实网络与键盘请求测试在开发包完成；不将这份证据写成 code 11 的新付费请求。详见 [1.1.0 插件与 DeepSeek 验收](validation/2026-10-04-1.1.0-plugins-deepseek.md) 和 [跨平台交付记录](../../validation/release-1.1.0/delivery.md)。
- 开发包与正式包 applicationId 不同。发布不会删除开发包或用户词库，也不声称二者可原地继承数据。
- Android 8.0、更多厂商与长期日用覆盖仍需后续验证。发布资产需带对应版本、code、源码提交、SHA256SUMS 和签名信息。
