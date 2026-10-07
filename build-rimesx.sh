#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================================="
echo " [!] 注意：RIMES X 已全面启用 GitHub 云端编译，禁止在本机执行重度编译！"
echo "=========================================================================="
echo "原因：本机属于 ARM64 架构，无 native x86_64 加速，本地编译会导致高负载与耗时。"
echo ""
echo "请使用以下方式在 GitHub 云端进行极速编译与打包："
echo "1. 推送代码至 main 分支自动触发云端编译；"
echo "2. 访问 GitHub Actions 页面手动点击触发："
echo "   https://github.com/Ysilicom/rimesx/actions"
echo ""
echo "编译完成后可在 GitHub Actions 的 Artifacts 区域直接下载已签名的 APK 成品。"
echo "=========================================================================="
exit 1
