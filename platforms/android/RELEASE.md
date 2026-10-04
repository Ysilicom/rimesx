# Android 1.0.0 发布准备

产品版本由 `VERSION` 提供，当前为 **1.0.0**，`versionCode=9`。每次对外提供新构建继续递增 code，不改变产品版本。正式身份为 `org.scholay.rimes.android`、名称 RIMES；开发包仍为 `.debug`、名称 RIMES Dev / RIMES 开发版。

## 本地构建

普通 `:app:assembleRelease :app:bundleRelease` 在没有签名材料时生成未签名产物，仅供检查。它们不是可交付的正式 release。正式签名由以下四个环境变量提供，不能写进源码或命令参数：

- `RIMES_ANDROID_KEYSTORE`：长期保管的正式 keystore 的绝对路径。
- `RIMES_ANDROID_STORE_PASSWORD`、`RIMES_ANDROID_KEY_ALIAS`、`RIMES_ANDROID_KEY_PASSWORD`。

配置完成后执行 `bash scripts/build-release.sh`。脚本运行核心测试、Release lint、APK/AAB 构建、APK 签名验证及 16 KB 对齐验证，在 `dist/1.0.0/` 保存产物与 SHA256SUMS。没有签名时直接停止，不回退到 debug keystore，不安装或上传。

## 仍需完成

1. 维护者选定并离线备份长期签名密钥，记录公开证书 SHA-256；不能使用临时测试密钥作为发行身份。
2. 现有 `.debug` 安装与正式 applicationId 不同，不能原地覆盖继承数据。新装与数据迁移需分别验收；在迁移验证前保留开发包和用户词库。
3. 将 dev8 的真机输入、CometAPI、取消与隐私检查在最终**已签名** APK 上重跑，补 Android 8.0 与不同厂商覆盖。CometAPI 可用性受服务商地区与账户条件影响。
4. 冻结实际源码快照、依赖与第三方许可材料，记录 APK/AAB 哈希、证书指纹、版本、code 和实际验收。未完成上述项目前，1.0.0 是发布目标，不是已公开版本。

2026-10-03 的 dev8 证据保留在 `validation/2026-10-03-dev8.md`；它不自动证明新正式包通过验收。
