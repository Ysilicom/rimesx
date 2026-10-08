#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# cloud-build-and-sign.sh
# 自动化触发 GitHub Actions 云端 R8 Release 编译 -> 定时监控状态 -> 本地私钥重签名
# 参考 Haven 项目设计，本地零算力消耗，云端极速打包
# ==============================================================================

REPO="Ysilicom/rimesx"
BRANCH="main"
WORKFLOW_NAME="Build RIMES X Release APK"
ARTIFACT_NAME="rimesx-release-apk"
OUTPUT_DIR="${OUTPUT_DIR:-./build-output}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"
mkdir -p "$OUTPUT_DIR"

AUTH_TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-$(gh auth token 2>/dev/null || true)}}"
CURL_AUTH=()
if [ -n "$AUTH_TOKEN" ]; then
    CURL_AUTH=(-H "Authorization: Bearer $AUTH_TOKEN")
fi

echo "================================================================="
echo "  RIMES X 云端编译与自动化私钥重签名流程 (参考 Haven)"
echo "  仓库: $REPO"
echo "  构建模式: assembleRelease (包含 R8 压缩优化、120Hz 优化与资源打包)"
echo "================================================================="

RUN_ID="${1:-}"

if [ -z "$RUN_ID" ]; then
    CURRENT_HEAD="$(git rev-parse HEAD)"
    RUNS_JSON=$(curl -s "${CURL_AUTH[@]}" "https://api.github.com/repos/$REPO/actions/runs?head_sha=$CURRENT_HEAD")
    EXISTING_RUN_ID=$(echo "$RUNS_JSON" | jq -r '.workflow_runs[]? | select(.name=="'"$WORKFLOW_NAME"'" and (.status=="in_progress" or .status=="queued")) | .id' | head -n 1)
    if [ -n "$EXISTING_RUN_ID" ] && [ "$EXISTING_RUN_ID" != "null" ]; then
        echo "ℹ️ 检测到当前 HEAD 存在进行中的 CI 构建任务 (Run ID: $EXISTING_RUN_ID)，直接恢复监控..."
        RUN_ID="$EXISTING_RUN_ID"
        RUN_URL=$(echo "$RUNS_JSON" | jq -r '.workflow_runs[]? | select(.id=='"$RUN_ID"') | .html_url')
        echo "  网页监控地址: $RUN_URL"
    fi
fi

if [ -z "$RUN_ID" ]; then
    CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
    if [ "$CURRENT_BRANCH" != "$BRANCH" ]; then
        echo "⚠️ 当前分支为 $CURRENT_BRANCH，建议切换到 $BRANCH 分支运行。"
    fi

    git fetch origin "$BRANCH" 2>/dev/null || true
    LOCAL_SHA="$(git rev-parse HEAD)"
    REMOTE_SHA="$(git rev-parse "origin/$BRANCH")"

    if [ "$LOCAL_SHA" = "$REMOTE_SHA" ]; then
        echo "ℹ️ 本地代码与远程一致，正在创建空提交触发云端 CI 构建..."
        git commit --allow-empty -m "ci: trigger RIMES X release build with R8 [$(date -u +'%Y-%m-%d %H:%M:%S UTC')]"
        git push origin "$BRANCH"
        TARGET_SHA="$(git rev-parse HEAD)"
    else
        echo "ℹ️ 检测到未推送的提交，正在推送到远程 origin/$BRANCH..."
        git push origin "$BRANCH"
        TARGET_SHA="$(git rev-parse HEAD)"
    fi

    echo "✓ 提交已推送到 GitHub, 目标 commit SHA: $TARGET_SHA"
    echo ""

    echo "⏳ 正在等待 GitHub Actions 注册并启动工作流..."
    for i in {1..25}; do
        RUNS_JSON=$(curl -s -A "curl/7.88" "${CURL_AUTH[@]}" "https://api.github.com/repos/$REPO/actions/runs?head_sha=$TARGET_SHA" 2>/dev/null || true)
        RUN_ID=$(echo "$RUNS_JSON" | jq -r '.workflow_runs[]? | select(.name=="'"$WORKFLOW_NAME"'") | .id' 2>/dev/null | head -n 1 || true)
        if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ]; then
            RUN_URL="https://github.com/$REPO/actions/runs/$RUN_ID"
            echo "✓ 成功检测到工作流 Run ID: $RUN_ID"
            echo "  网页监控地址: $RUN_URL"
            break
        fi
        sleep 3
    done

    if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
        echo "❌ 超时：未在 GitHub Actions 中检测到对应 commit 的构建任务，请检查 GitHub 仓库设置。" >&2
        exit 1
    fi
fi

echo ""
echo "================================================================="
echo "  开始定时监控云端编译进度..."
echo "================================================================="

POLL_INTERVAL="${POLL_INTERVAL:-180}"
STATUS="in_progress"
CONCLUSION=""

while true; do
    TIME_STR=$(date +'%H:%M:%S')
    RUN_DATA=$(curl -s "${CURL_AUTH[@]}" "https://api.github.com/repos/$REPO/actions/runs/$RUN_ID")
    STATUS=$(echo "$RUN_DATA" | jq -r '.status // empty')
    CONCLUSION=$(echo "$RUN_DATA" | jq -r '.conclusion // empty')

    if [ "$STATUS" != "in_progress" ] && [ "$STATUS" != "completed" ] && [ "$STATUS" != "queued" ]; then
        PAGE_HTML=$(curl -sL "https://github.com/$REPO/actions/runs/$RUN_ID" 2>/dev/null || true)
        if echo "$PAGE_HTML" | grep -q 'currently running:' || echo "$PAGE_HTML" | grep -q 'data-concluded="false"'; then
            STATUS="in_progress"
            echo "[$TIME_STR] 云端构建进行中 (Build & Sign RIMES X APK running)..."
        elif echo "$PAGE_HTML" | grep -q 'aria-label="completed successfully: "'; then
            STATUS="completed"
            CONCLUSION="success"
            echo "[$TIME_STR] 云端构建已结束 (结果: success)"
        elif echo "$PAGE_HTML" | grep -q 'aria-label="failed: "'; then
            STATUS="completed"
            CONCLUSION="failure"
            echo "[$TIME_STR] 云端构建已结束 (结果: failure)"
        else
            STATUS="in_progress"
            echo "[$TIME_STR] 正在获取云端状态..."
        fi
    else
        echo "[$TIME_STR] 总体状态: $STATUS | 结果: ${CONCLUSION:-进行中}"
        JOBS_DATA=$(curl -s "${CURL_AUTH[@]}" "https://api.github.com/repos/$REPO/actions/runs/$RUN_ID/jobs" 2>/dev/null || true)
        echo "$JOBS_DATA" | jq -r '.jobs[]? | "  - \(.name): \(.status) (\(.conclusion // "running"))"' 2>/dev/null || true
    fi

    if [ "$STATUS" = "completed" ]; then
        break
    fi

    echo "  (等待 ${POLL_INTERVAL} 秒后刷新...)"
    sleep "$POLL_INTERVAL"
done

echo ""
echo "================================================================="
if [ "$CONCLUSION" != "success" ]; then
    echo "❌ 云端构建未能成功完成 (conclusion: $CONCLUSION)"
    echo "请访问详情排查: https://github.com/$REPO/actions/runs/$RUN_ID"
    exit 1
fi

echo "🎉 云端编译成功完成！"
echo "================================================================="

# 下载制品并使用本地永久私钥自签名
DOWNLOADED_APK=""

echo "🌐 正在获取并下载云端制品 ($ARTIFACT_NAME)..."
if [ -n "$AUTH_TOKEN" ]; then
    ARTIFACTS_DATA=$(curl -s "${CURL_AUTH[@]}" "https://api.github.com/repos/$REPO/actions/runs/$RUN_ID/artifacts")
    ARTIFACT_ID=$(echo "$ARTIFACTS_DATA" | jq -r '.artifacts[] | select(.name=="'"$ARTIFACT_NAME"'") | .id' | head -n 1)
    if [ -n "$ARTIFACT_ID" ] && [ "$ARTIFACT_ID" != "null" ]; then
        curl -sL -H "Authorization: Bearer $AUTH_TOKEN" \
             -H "Accept: application/vnd.github+json" \
             "https://api.github.com/repos/$REPO/actions/artifacts/$ARTIFACT_ID/zip" \
             -o "$OUTPUT_DIR/rimesx-artifact.zip"
        unzip -o "$OUTPUT_DIR/rimesx-artifact.zip" -d "$OUTPUT_DIR/"
        DOWNLOADED_APK=$(find "$OUTPUT_DIR" -name "*.apk" 2>/dev/null | sort -V | tail -n 1)
    fi
fi

if [ -z "$DOWNLOADED_APK" ] || [ ! -f "$DOWNLOADED_APK" ]; then
    echo "尝试通过 nightly.link 获取云端制品..."
    for retry in {1..8}; do
        if curl -sL -f "https://nightly.link/$REPO/actions/runs/$RUN_ID/$ARTIFACT_NAME.zip" -o "$OUTPUT_DIR/rimesx-artifact.zip"; then
            unzip -o "$OUTPUT_DIR/rimesx-artifact.zip" -d "$OUTPUT_DIR/"
            DOWNLOADED_APK=$(find "$OUTPUT_DIR" -name "*.apk" 2>/dev/null | sort -V | tail -n 1)
            if [ -n "$DOWNLOADED_APK" ] && [ -f "$DOWNLOADED_APK" ]; then
                echo "✓ 成功获取并解压制品: $DOWNLOADED_APK"
                break
            fi
        fi
        echo "  (等待 nightly.link 同步制品中，5秒后重试第 $retry/8 次...)"
        sleep 5
    done
fi

if [ -z "$DOWNLOADED_APK" ] || [ ! -f "$DOWNLOADED_APK" ]; then
    echo "⚠️ 自动下载制品未成功，请在浏览器中打开下载："
    echo "👉 https://github.com/$REPO/actions/runs/$RUN_ID"
    echo "下载后将 .apk 放入 $OUTPUT_DIR/ 目录。"
    DOWNLOADED_APK=$(find "$OUTPUT_DIR" -name "*.apk" 2>/dev/null | sort -V | tail -n 1)
fi

if [ -n "$DOWNLOADED_APK" ] && [ -f "$DOWNLOADED_APK" ]; then
    echo ""
    echo "🚀 正在使用本地永久私钥重签名: $DOWNLOADED_APK"
    bash "$ROOT_DIR/platforms/android/scripts/sign-rimesx.sh" "$DOWNLOADED_APK"
    echo ""
    echo "✅ 重签名完成！最终可安装 APK: $DOWNLOADED_APK"
else
    echo ""
    echo "💡 将 APK 放入 $OUTPUT_DIR 后，可随时手动运行:"
    echo "   bash platforms/android/scripts/sign-rimesx.sh"
fi
