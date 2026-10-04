import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type ReactNode,
} from "react";
import { Icon, type IconName } from "../design-system/Icon";
import { initialPlugins } from "../design-system/data";
import { IconButton } from "../design-system/primitives";

export type BufferMode =
  | "normal"
  | "ai"
  | "translation"
  | "stream";
export type BufferPhase = "idle" | "loading" | "ready" | "protected" | "error";

export type BufferLanguage = {
  value: string;
  label: string;
};

export type BufferSendAcknowledgement = boolean;
export type BufferAIConnector = "codex" | "claude" | "openai";

/**
 * Each AI backend is its own Buffer channel plug-in. It sends the source
 * buffer to that one backend as-is; there is no task prompt or picker.
 */
export const AI_CHANNEL_PLUGINS: Record<BufferAIConnector, {
  id: string;
  backend: string;
}> = {
  codex: { id: "builtin.codex-cli", backend: "Codex" },
  claude: { id: "builtin.claude-code-cli", backend: "Claude Code CLI" },
  openai: { id: "builtin.openai-compatible", backend: "OpenAI 兼容 API" },
};

const AI_CONNECTOR_ORDER: readonly BufferAIConnector[] = ["codex", "claude", "openai"];

type AIInferenceConfiguration = { model: string; effort: string };
const CODEX_MODELS = ["gpt-6-astra", "gpt-6-sol", "gpt-6-luna", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna"];
const CLAUDE_MODELS = ["claude-opus-5-5", "opus", "sonnet", "haiku"];

export type BufferPluginConfiguration =
  | {
    mode: "ai";
    connector: BufferAIConnector;
  }
  | {
    mode: "translation";
    sourceLanguage: string;
    targetLanguage: string;
    translateContinuously: boolean;
  }
  | {
    mode: "stream";
    candidateCount: number;
    latency: "fast" | "balanced" | "stable";
  };

export type BufferTargetContext = {
  requestID: string | number;
  contextKey: string;
};

export type BufferExternalSource = {
  revision: number;
  sourceLabel: string;
  text: string;
};

export type BufferGenerationContext = {
  requestID: number;
  contextKey: string;
  signal: AbortSignal;
};

export type BufferSurfaceProps = {
  mode?: BufferMode;
  defaultMode?: BufferMode;
  phase?: BufferPhase;
  defaultPhase?: BufferPhase;
  sourceText?: string;
  defaultSourceText?: string;
  targets?: readonly string[];
  defaultTargets?: readonly string[];
  activeRequestID?: string | number;
  targetContext?: BufferTargetContext;
  selectedTarget?: number;
  defaultSelectedTarget?: number;
  sourceLanguage?: string;
  defaultSourceLanguage?: string;
  targetLanguage?: string;
  defaultTargetLanguage?: string;
  translationContinuously?: boolean;
  /** Initial state of the translation read-aloud switch. Off by default. */
  defaultReadAloud?: boolean;
  onReadAloudChange?: (enabled: boolean) => void;
  aiConnector?: BufferAIConnector;
  aiConfigurations?: Partial<Record<BufferAIConnector, AIInferenceConfiguration>>;
  /** Temporary provider summary. Never becomes a target that can be sent. */
  thinkingText?: string;
  streamCandidateCount?: number;
  streamLatency?: "fast" | "balanced" | "stable";
  externalSource?: BufferExternalSource;
  paused?: boolean;
  languages?: readonly BufferLanguage[];
  availablePluginIDs?: readonly string[];
  className?: string;
  onModeChange?: (mode: BufferMode) => void;
  onPhaseChange?: (phase: BufferPhase) => void;
  onSourceChange?: (text: string) => void;
  onTargetsChange?: (targets: readonly string[]) => void;
  onTargetSelect?: (index: number, target: string) => void;
  onLanguageChange?: (sourceLanguage: string, targetLanguage: string) => void;
  onPluginConfigurationChange?: (configuration: BufferPluginConfiguration) => void;
  onAIInferenceChange?: (connector: BufferAIConnector, configuration: AIInferenceConfiguration) => void;
  onOpenPluginSettings?: (pluginID: string) => void;
  onGenerate?: (
    mode: BufferMode,
    sourceText: string,
    context: BufferGenerationContext,
  ) => void;
  onSend?: (
    text: string,
    mode: BufferMode,
    signal: AbortSignal,
  ) => BufferSendAcknowledgement | Promise<BufferSendAcknowledgement>;
  onClose?: () => void;
};

type ModeDescriptor = {
  label: string;
  icon: IconName;
  action: string;
  loadingAction: string;
};

const MODE_ORDER: readonly BufferMode[] = [
  "normal",
  "ai",
  "translation",
  "stream",
];

export const BUFFER_MODE_PLUGIN_IDS = {
  translation: "builtin.apple-translation",
  stream: "builtin.stream-input",
} as const satisfies Record<Exclude<BufferMode, "normal" | "ai">, string>;

export function bufferPluginIDFor(
  mode: Exclude<BufferMode, "normal">,
  aiConnector: BufferAIConnector = "codex",
): string {
  return mode === "ai" ? AI_CHANNEL_PLUGINS[aiConnector].id : BUFFER_MODE_PLUGIN_IDS[mode];
}

function aiChannelLabel(connector: BufferAIConnector): string {
  return pluginCatalog.get(AI_CHANNEL_PLUGINS[connector].id)?.name ?? AI_CHANNEL_PLUGINS[connector].backend;
}

const pluginCatalog = new Map(initialPlugins.map((plugin) => [plugin.id, plugin]));
const translationPlugin = pluginCatalog.get("builtin.apple-translation");
const streamPlugin = pluginCatalog.get("builtin.stream-input");

const MODE_DESCRIPTORS: Record<BufferMode, ModeDescriptor> = {
  normal: {
    label: "Default",
    icon: "grid",
    action: "发送",
    loadingAction: "发送中…",
  },
  translation: {
    label: translationPlugin?.name ?? "实时翻译",
    icon: translationPlugin?.icon ?? "globe",
    action: "翻译",
    loadingAction: "翻译中…",
  },
  ai: {
    label: aiChannelLabel("codex"),
    icon: "sparkle",
    action: "请求",
    loadingAction: "请求中…",
  },
  stream: {
    label: streamPlugin?.name ?? "意识流输入",
    icon: streamPlugin?.icon ?? "waveform",
    action: "推测",
    loadingAction: "推测中…",
  },
};

const DEFAULT_LANGUAGES: readonly BufferLanguage[] = [
  { value: "auto", label: "自动检测" },
  { value: "zh-Hans", label: "简体中文" },
  { value: "zh-Hant", label: "繁体中文" },
  { value: "en", label: "英语" },
  { value: "ja", label: "日语" },
  { value: "ko", label: "韩语" },
  { value: "fr", label: "法语" },
  { value: "de", label: "德语" },
  { value: "es", label: "西班牙语" },
];

const DEFAULT_SOURCE = "请把这段缓冲内容整理为一段清晰的产品说明。";
const LIVE_GENERATION_DEBOUNCE_MS = 420;
const LIVE_GENERATION_PREVIEW_MS = 420;
const EXCHANGE_GENERATION_PREVIEW_MS = 700;

function clampTargetIndex(index: number, targetCount: number): number {
  if (targetCount <= 0 || !Number.isFinite(index)) return 0;
  return Math.min(Math.max(Math.trunc(index), 0), targetCount - 1);
}

function defaultTargetsFor(_mode: BufferMode): readonly string[] {
  // All plugins open on the writing rail. Live-expand fills the lower rail
  // after typed content arrives; exchange plugins swap this rail after request.
  return [];
}

function bufferLayoutFor(mode: BufferMode): "single-exchange" | "live-expand" {
  // Live retrieval / parallel drafting: grow a lower rail while typing.
  return mode === "translation" || mode === "stream"
    ? "live-expand"
    : "single-exchange";
}

export function bufferInputContextKey(
  mode: BufferMode,
  sourceText: string,
  sourceLanguage: string,
  targetLanguage: string,
  streamCandidateCount: number,
  streamLatency: "fast" | "balanced" | "stable",
  aiConnector: BufferAIConnector = "codex",
): string {
  if (mode === "translation") {
    return JSON.stringify([
      mode,
      sourceText,
      sourceLanguage,
      targetLanguage,
    ]);
  }
  if (mode === "stream") {
    return JSON.stringify([mode, sourceText, streamCandidateCount, streamLatency]);
  }
  if (mode === "ai") return JSON.stringify([mode, sourceText, aiConnector]);
  return JSON.stringify([mode, sourceText]);
}

function generatedTargetsFor(
  mode: BufferMode,
  source: string,
  streamCandidateCount = 5,
  aiConnector: BufferAIConnector = "codex",
): readonly string[] {
  const conciseSource = source.trim() || "当前缓冲内容";
  switch (mode) {
    case "normal":
      return [];
    case "translation":
      return [
        `Apple 本地译文：${conciseSource}`,
      ];
    case "ai":
      switch (aiConnector) {
        case "codex":
          return [`Codex 回复：${conciseSource}`];
        case "claude":
          return [`Claude Code 回复：${conciseSource}`];
        case "openai":
          return [`AI API 回复：${conciseSource}`];
      }
    case "stream":
      return [
        `${conciseSource}`,
        `${conciseSource}，并保持表达简洁。`,
        `请整理：${conciseSource}`,
        `把“${conciseSource}”改成可直接发送的说明。`,
        `基于“${conciseSource}”给出更口语的版本。`,
      ].slice(0, Math.min(5, Math.max(1, Math.trunc(streamCandidateCount))));
  }
}

function useControllableState<T>(
  value: T | undefined,
  initialValue: T,
  onChange?: (next: T) => void,
) {
  const [internalValue, setInternalValue] = useState(initialValue);
  const resolvedValue = value ?? internalValue;

  const setValue = (next: T) => {
    if (value === undefined) setInternalValue(next);
    onChange?.(next);
  };

  return [resolvedValue, setValue] as const;
}

/** Only surface status when it changes what the user should do next.
 *  Idle "可发送" / "等待内容" is redundant with the send button and rails. */
function statusFor(mode: BufferMode, phase: BufferPhase, _hasContent: boolean): string | null {
  if (phase === "protected") return "安全输入，内容已隐藏";
  if (phase === "loading") {
    if (mode === "translation") return "正在翻译";
    if (mode === "stream") return "正在推测完整输入";
    return mode === "normal" ? "正在发送" : "插件正在生成";
  }
  if (phase === "error") return "处理失败";
  return null;
}

export async function copyBufferResult(text: string): Promise<boolean> {
  if (!text || typeof navigator === "undefined" || !navigator.clipboard?.writeText) return false;
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

const SPEECH_LANGUAGES: Record<string, string> = {
  "zh-Hans": "zh-CN",
  "zh-Hant": "zh-TW",
};

/**
 * Reads a finished result aloud with the platform's on-device voices. Returns
 * a stop function, or null when this browser has no speech synthesis.
 */
export function speakBufferResult(
  text: string,
  language: string,
  onEnd: () => void,
): (() => void) | null {
  const synthesis = typeof window === "undefined" ? undefined : window.speechSynthesis;
  if (!text.trim() || !synthesis || typeof SpeechSynthesisUtterance === "undefined") return null;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = SPEECH_LANGUAGES[language] ?? language;
  let settled = false;
  const settle = () => {
    if (settled) return;
    settled = true;
    onEnd();
  };
  utterance.onend = settle;
  utterance.onerror = settle;
  synthesis.cancel();
  synthesis.speak(utterance);
  return () => {
    synthesis.cancel();
    settle();
  };
}

function BufferTrack({
  kind,
  leadingControl,
  protectedContent,
  loading,
  loadingLabel,
  thinkingText,
  status,
  statusTone = "ready",
  sourceValue,
  targets,
  selectedTarget = 0,
  emptyLabel,
  onSourceChange,
  onTargetSelect,
  onCopy,
  copyDisabled = false,
  onTargetSpeak,
  interactionDisabled = false,
}: {
  kind: "source" | "target";
  leadingControl?: ReactNode;
  protectedContent: boolean;
  loading: boolean;
  loadingLabel?: string;
  thinkingText?: string;
  status?: string | null;
  statusTone?: BufferPhase;
  sourceValue?: string;
  targets?: readonly string[];
  selectedTarget?: number;
  emptyLabel?: string;
  onSourceChange?: (text: string) => void;
  onTargetSelect?: (index: number) => void;
  onCopy?: () => void;
  copyDisabled?: boolean;
  /** Set while translation read-aloud is on: clicking a block reads it. */
  onTargetSpeak?: (text: string) => void;
  interactionDisabled?: boolean;
}) {
  const [sourceFocused, setSourceFocused] = useState(false);
  const targetCount = targets?.length ?? 0;
  const activeIndex = clampTargetIndex(selectedTarget, targetCount);
  const activeTarget = targetCount > 0 ? targets![activeIndex] : "";
  const statusDescription = kind === "target" && loading
    ? loadingLabel ?? "正在处理"
    : status ?? (kind === "target" && targetCount === 0
      ? emptyLabel ?? "等待结果" : null);
  const statusLoading = loading || statusTone === "loading";
  const statusIcon: IconName = statusLoading ? "refresh"
    : statusTone === "protected" ? "lock"
      : statusTone === "error" ? "warning" : "info";

  const stepTarget = (delta: number) => {
    if (interactionDisabled || targetCount <= 1) return;
    const next = (activeIndex + delta + targetCount) % targetCount;
    onTargetSelect?.(next);
  };

  return (
    <div
      aria-label={`${kind === "source" ? "源" : "目标"}缓冲轨道`}
      className={`buffer-track buffer-track--${kind}${protectedContent ? " is-protected" : ""}`}
    >
      {leadingControl}
      {kind === "target" && targetCount > 0 && onCopy ? (
        <IconButton
          className="buffer-track__copy"
          disabled={copyDisabled || interactionDisabled}
          icon="copy"
          label="复制当前结果并关闭 Buffer"
          onClick={onCopy}
        />
      ) : null}

      <div className="buffer-track__content">
        {protectedContent ? (
          <span className="buffer-track__protected-message">
            <Icon name="lock" size={13} />
            内容已隐藏
          </span>
        ) : kind === "source" ? (
          <input
            aria-label="缓冲正文"
            className="buffer-track__editor"
            disabled={interactionDisabled}
            onBlur={() => setSourceFocused(false)}
            onChange={(event) => onSourceChange?.(event.target.value)}
            onFocus={() => setSourceFocused(true)}
            placeholder={sourceFocused ? "" : "等待暂存内容"}
            spellCheck={false}
            type="text"
            value={sourceValue ?? ""}
          />
        ) : loading ? (
          thinkingText ? <span className="buffer-track__thinking" title={thinkingText}>
            {thinkingText}
          </span> : null
        ) : targetCount > 0 ? (
          <div
            aria-label={`候选 ${activeIndex + 1} / ${targetCount}`}
            className="buffer-track__pager"
            role="group"
          >
            {targetCount > 1 ? <span className="buffer-track__pager-stepper">
              <span className="buffer-track__pager-count" title={`${targetCount} 条候选`}>
                {activeIndex + 1}/{targetCount}
              </span>
              <span className="buffer-track__pager-controls">
                <button
                  aria-label="上一条候选"
                  className="buffer-track__pager-button"
                  disabled={interactionDisabled}
                  onClick={() => stepTarget(-1)}
                  type="button"
                >
                  <Icon name="up" size={11} weight="bold" />
                </button>
                <button
                  aria-label="下一条候选"
                  className="buffer-track__pager-button"
                  disabled={interactionDisabled}
                  onClick={() => stepTarget(1)}
                  type="button"
                >
                  <Icon name="down" size={11} weight="bold" />
                </button>
              </span>
            </span> : null}
            {onTargetSpeak ? (
              <button
                aria-label={`朗读此块：${activeTarget}`}
                className="buffer-track__pager-text buffer-track__pager-text--speakable"
                disabled={interactionDisabled}
                onClick={() => onTargetSpeak(activeTarget)}
                title={activeTarget}
                type="button"
              >
                {activeTarget}
              </button>
            ) : (
              <span className="buffer-track__pager-text" title={activeTarget}>
                {activeTarget}
              </span>
            )}
          </div>
        ) : null}
      </div>
      {statusDescription ? (
        <span aria-label={statusDescription} className={`buffer-track__status buffer-status--${statusTone}`}
          role="img" title={statusDescription}>
          <Icon className={statusLoading ? "is-spinning" : undefined}
            name={statusIcon} size={14} weight="bold" />
        </span>
      ) : null}
    </div>
  );
}

function BufferInputControl({
  mode,
  descriptor,
  availableModes,
  availableAIConnectors,
  configurationDisabled,
  protectedContent,
  sourceLanguage,
  targetLanguage,
  languageOptions,
  targetLanguageOptions,
  translationContinuously,
  readAloud,
  aiConnector,
  aiConfiguration,
  streamCandidateCount,
  streamLatency,
  canReturnToSource,
  canRetry,
  onModeChange,
  onSourceLanguageChange,
  onTargetLanguageChange,
  onSwapLanguages,
  onTranslationContinuouslyChange,
  onReadAloudChange,
  onAIConnectorChange,
  onAIInferenceChange,
  onStreamCandidateCountChange,
  onStreamLatencyChange,
  onReturnToSource,
  onRetry,
  onOpenSettings,
  onClose,
}: {
  mode: BufferMode;
  descriptor: ModeDescriptor;
  availableModes: readonly BufferMode[];
  availableAIConnectors: readonly BufferAIConnector[];
  configurationDisabled: boolean;
  protectedContent: boolean;
  sourceLanguage: string;
  targetLanguage: string;
  languageOptions: readonly BufferLanguage[];
  targetLanguageOptions: readonly BufferLanguage[];
  translationContinuously: boolean;
  readAloud: boolean;
  aiConnector: BufferAIConnector;
  aiConfiguration: AIInferenceConfiguration;
  streamCandidateCount: number;
  streamLatency: "fast" | "balanced" | "stable";
  canReturnToSource: boolean;
  canRetry: boolean;
  onModeChange: (mode: BufferMode) => void;
  onSourceLanguageChange: (event: ChangeEvent<HTMLSelectElement>) => void;
  onTargetLanguageChange: (event: ChangeEvent<HTMLSelectElement>) => void;
  onSwapLanguages: () => void;
  onTranslationContinuouslyChange: (continuous: boolean) => void;
  onReadAloudChange: (enabled: boolean) => void;
  onAIConnectorChange: (connector: BufferAIConnector) => void;
  onAIInferenceChange: (configuration: AIInferenceConfiguration) => void;
  onStreamCandidateCountChange: (count: number) => void;
  onStreamLatencyChange: (latency: "fast" | "balanced" | "stable") => void;
  onReturnToSource: () => void;
  onRetry: () => void;
  onOpenSettings?: () => void;
  onClose?: () => void;
}) {
  return (
    <div className="buffer-input-control">
      <span
        aria-hidden="true"
        className="buffer-input-control__trigger"
        title={descriptor.label}
      >
        <Icon name={protectedContent ? "lock" : descriptor.icon} size={14} weight="bold" />
      </span>

      <section
        aria-label="Buffer 工具栏"
        className="buffer-toolbar"
        data-native-window-drag-region="true"
        id="buffer-toolbar"
        role="toolbar"
      >
          <header className="buffer-plugin-popover__header">
            <span>
              <strong>工作插件</strong>
              <small>选择当前工作流，并直接调整对应配置。</small>
            </span>
          </header>

          <label className="buffer-plugin-popover__field">
            <span>当前插件</span>
            <select
              aria-label="工作台插件"
              disabled={configurationDisabled}
              onChange={(event) => {
                // AI channel plug-ins share the "ai" behavior; the suffix names which one.
                const [nextMode, connector] = event.target.value.split(":");
                if (connector) onAIConnectorChange(connector as BufferAIConnector);
                onModeChange(nextMode as BufferMode);
              }}
              value={mode === "ai" ? `ai:${aiConnector}` : mode}
            >
              {availableModes.flatMap((pluginMode) => (
                pluginMode === "ai"
                  ? availableAIConnectors.map((connector) => (
                    <option key={`ai:${connector}`} value={`ai:${connector}`}>
                      {aiChannelLabel(connector)}
                    </option>
                  ))
                  : [(
                    <option key={pluginMode} value={pluginMode}>
                      {MODE_DESCRIPTORS[pluginMode].label}
                    </option>
                  )]
              ))}
            </select>
          </label>

          {mode === "normal" ? (
            <p className="buffer-plugin-popover__empty">Default 没有额外配置。</p>
          ) : null}

          {mode === "translation" ? (
            <div className="buffer-plugin-popover__configuration">
              <div className="buffer-plugin-popover__language-row">
                <label className="buffer-plugin-popover__field">
                  <span>源语言</span>
                  <select
                    aria-label="源语言"
                    disabled={configurationDisabled}
                    onChange={onSourceLanguageChange}
                    value={sourceLanguage}
                  >
                    {languageOptions.map((language) => (
                      <option key={`source-${language.value}`} value={language.value}>{language.label}</option>
                    ))}
                  </select>
                </label>
                <button
                  aria-label="交换源语言和目标语言"
                  className="buffer-plugin-popover__swap"
                  disabled={configurationDisabled}
                  onClick={onSwapLanguages}
                  type="button"
                >
                  交换
                </button>
                <label className="buffer-plugin-popover__field">
                  <span>目标语言</span>
                  <select
                    aria-label="目标语言"
                    disabled={configurationDisabled}
                    onChange={onTargetLanguageChange}
                    value={targetLanguage}
                  >
                    {targetLanguageOptions.map((language) => (
                      <option key={`target-${language.value}`} value={language.value}>{language.label}</option>
                    ))}
                  </select>
                </label>
                <IconButton
                  aria-pressed={readAloud}
                  className={`buffer-plugin-popover__speech${readAloud ? " is-on" : ""}`}
                  disabled={configurationDisabled}
                  icon={readAloud ? "speak" : "speakOff"}
                  label="朗读译文"
                  onClick={() => onReadAloudChange(!readAloud)}
                  title={readAloud ? "朗读已开启：点按译文块或发送时朗读该块" : "朗读已关闭"}
                />
              </div>
              <label className="buffer-plugin-popover__check">
                <input
                  checked={translationContinuously}
                  disabled={configurationDisabled}
                  onChange={(event) => onTranslationContinuouslyChange(event.target.checked)}
                  type="checkbox"
                />
                <span>连续翻译</span>
              </label>
            </div>
          ) : null}

          {mode === "ai" && aiConnector !== "openai" ? (
            <div className="buffer-plugin-popover__configuration">
              <label className="buffer-plugin-popover__field">
                <span>模型</span>
                <select aria-label={`${aiChannelLabel(aiConnector)} 模型`} disabled={configurationDisabled}
                  onChange={(event) => onAIInferenceChange({ ...aiConfiguration, model: event.target.value })}
                  value={aiConfiguration.model}>
                  {(aiConnector === "codex" ? CODEX_MODELS : CLAUDE_MODELS).map((model) => (
                    <option key={model} value={model}>{model === "claude-opus-5-5" ? "Claude Opus 5.5" : model}</option>
                  ))}
                </select>
              </label>
              <label className="buffer-plugin-popover__field">
                <span>推理深度</span>
                <select aria-label={`${aiChannelLabel(aiConnector)} 推理深度`} disabled={configurationDisabled}
                  onChange={(event) => onAIInferenceChange({ ...aiConfiguration, effort: event.target.value })}
                  value={aiConfiguration.effort}>
                  {(aiConnector === "codex"
                    ? ["low", "medium", "high", "xhigh", "max", "ultra"]
                    : ["low", "medium", "high", "xhigh", "max"]
                  ).map((effort) => <option key={effort} value={effort}>{effort}</option>)}
                </select>
              </label>
            </div>
          ) : mode === "ai" ? (
            <p className="buffer-plugin-popover__empty">AI API 模型在连接器中选择。</p>
          ) : null}

          {mode === "stream" ? (
            <div className="buffer-plugin-popover__configuration">
              <label className="buffer-plugin-popover__field">
                <span>候选数量</span>
                <select
                  aria-label="意识流候选数量"
                  disabled={configurationDisabled}
                  onChange={(event) => onStreamCandidateCountChange(Number(event.target.value))}
                  value={streamCandidateCount}
                >
                  {[1, 2, 3, 4, 5].map((count) => (
                    <option key={count} value={count}>{count} 个</option>
                  ))}
                </select>
              </label>
              <label className="buffer-plugin-popover__field">
                <span>响应节奏</span>
                <select
                  aria-label="意识流响应节奏"
                  disabled={configurationDisabled}
                  onChange={(event) => onStreamLatencyChange(
                    event.target.value as "fast" | "balanced" | "stable",
                  )}
                  value={streamLatency}
                >
                  <option value="fast">灵敏</option>
                  <option value="balanced">平衡</option>
                  <option value="stable">稳定</option>
                </select>
              </label>
            </div>
          ) : null}

          <footer className="buffer-plugin-popover__actions">
            {canReturnToSource ? (
              <button onClick={onReturnToSource} type="button">编辑原文</button>
            ) : null}
            {canRetry ? (
              <button aria-label="重新生成" onClick={onRetry} type="button">重试</button>
            ) : null}
            {mode !== "normal" && onOpenSettings ? (
              <button
                aria-label="在设置中打开完整配置"
                onClick={onOpenSettings}
                type="button"
              >
                插件设置
              </button>
            ) : null}
            {onClose ? (
              <button
                aria-label="关闭并暂停缓冲（保留内容）"
                className="is-danger"
                onClick={onClose}
                type="button"
              >
                关闭
              </button>
            ) : null}
          </footer>
        </section>
    </div>
  );
}

/**
 * Interactive visual mirror of the native Buffer workbench. This component
 * owns demo state when props are omitted, while every meaningful state can also
 * be controlled by a consuming prototype.
 */
export function BufferSurface({
  mode: controlledMode,
  defaultMode = "normal",
  phase: controlledPhase,
  defaultPhase = "ready",
  sourceText: controlledSourceText,
  defaultSourceText = DEFAULT_SOURCE,
  targets: controlledTargets,
  defaultTargets,
  activeRequestID,
  targetContext,
  selectedTarget: controlledSelectedTarget,
  defaultSelectedTarget = 0,
  sourceLanguage: controlledSourceLanguage,
  defaultSourceLanguage = "zh-Hans",
  targetLanguage: controlledTargetLanguage,
  defaultTargetLanguage = "en",
  translationContinuously: requestedTranslationContinuously = true,
  defaultReadAloud: requestedReadAloud = false,
  onReadAloudChange,
  aiConnector: requestedAIConnector = "codex",
  aiConfigurations,
  thinkingText,
  streamCandidateCount: requestedStreamCandidateCount = 5,
  streamLatency: requestedStreamLatency = "balanced",
  externalSource,
  paused = false,
  languages = DEFAULT_LANGUAGES,
  availablePluginIDs,
  className = "",
  onModeChange,
  onPhaseChange,
  onSourceChange,
  onTargetsChange,
  onTargetSelect,
  onLanguageChange,
  onPluginConfigurationChange,
  onAIInferenceChange,
  onOpenPluginSettings,
  onGenerate,
  onSend,
  onClose,
}: BufferSurfaceProps) {
  const initialMode = controlledMode ?? defaultMode;
  const [mode, setMode] = useControllableState(controlledMode, defaultMode, onModeChange);
  const [phase, setPhase] = useControllableState(controlledPhase, defaultPhase, onPhaseChange);
  const [sourceText, setSourceText] = useControllableState(
    controlledSourceText,
    defaultSourceText,
    onSourceChange,
  );
  const [unboundedTargets, setTargets] = useControllableState<readonly string[]>(
    controlledTargets,
    defaultTargets ?? defaultTargetsFor(initialMode),
    onTargetsChange,
  );
  const targets = useMemo(
    () => unboundedTargets.slice(0, 5),
    [unboundedTargets],
  );
  const [selectedTarget, setSelectedTarget] = useControllableState(
    controlledSelectedTarget,
    defaultSelectedTarget,
  );
  const [sourceLanguage, setSourceLanguage] = useControllableState(
    controlledSourceLanguage,
    defaultSourceLanguage,
  );
  const [targetLanguage, setTargetLanguage] = useControllableState(
    controlledTargetLanguage,
    defaultTargetLanguage,
  );
  const [translationContinuously, setTranslationContinuously] = useState(
    requestedTranslationContinuously,
  );
  const [aiConnector, setAIConnector] = useState<BufferAIConnector>(requestedAIConnector);
  const [localAIConfigurations, setLocalAIConfigurations] = useState<Partial<Record<BufferAIConnector, AIInferenceConfiguration>>>({});
  const storedAIConfiguration = aiConfigurations?.[aiConnector]
    ?? localAIConfigurations[aiConnector];
  const aiConfiguration = {
    model: storedAIConfiguration?.model && storedAIConfiguration.model !== "default"
      ? storedAIConfiguration.model
      : aiConnector === "codex" ? "gpt-6-sol" : "claude-opus-5-5",
    effort: storedAIConfiguration?.effort && storedAIConfiguration.effort !== "default"
      ? storedAIConfiguration.effort : "medium",
  };
  const [streamCandidateCount, setStreamCandidateCount] = useState(
    Math.min(5, Math.max(1, Math.trunc(requestedStreamCandidateCount))),
  );
  const [streamLatency, setStreamLatency] = useState(requestedStreamLatency);
  const [deliveryNote, setDeliveryNote] = useState("");
  const [deliveryTone, setDeliveryTone] = useState<BufferPhase>("ready");
  const [sending, setSending] = useState(false);
  const [copying, setCopying] = useState(false);
  const [readAloud, setReadAloud] = useState(requestedReadAloud);
  const stopSpeech = useRef<(() => void) | null>(null);
  const [targetsAreCurrent, setTargetsAreCurrent] = useState(
    () => (controlledTargets ?? defaultTargets ?? []).length > 0,
  );
  const generationTimer = useRef<number | undefined>(undefined);
  const generationRevision = useRef(0);
  const generationAbortController = useRef<AbortController | null>(null);
  const [managedGenerationRequest, setManagedGenerationRequest] = useState<
    BufferTargetContext | null | undefined
  >(undefined);
  const managedGenerationRequestRef = useRef<BufferTargetContext | null | undefined>(undefined);
  const [settledGenerationRequest, setSettledGenerationRequest] = useState<
    BufferTargetContext | null
  >(null);
  const settledGenerationRequestRef = useRef<BufferTargetContext | null>(null);
  const deliveryRevision = useRef(0);
  const copyRevision = useRef(0);
  const deliveryAbortController = useRef<AbortController | null>(null);
  const onGenerateRef = useRef(onGenerate);
  const onSendRef = useRef(onSend);
  const onTargetSelectRef = useRef(onTargetSelect);
  const controlledPhaseRef = useRef(controlledPhase);
  const sendingRef = useRef(sending);
  onGenerateRef.current = onGenerate;
  onSendRef.current = onSend;
  onTargetSelectRef.current = onTargetSelect;
  controlledPhaseRef.current = controlledPhase;
  sendingRef.current = sending;

  useEffect(() => {
    setTranslationContinuously(requestedTranslationContinuously);
  }, [requestedTranslationContinuously]);

  useEffect(() => {
    setAIConnector(requestedAIConnector);
  }, [requestedAIConnector]);

  useEffect(() => {
    setStreamCandidateCount(
      Math.min(5, Math.max(1, Math.trunc(requestedStreamCandidateCount))),
    );
  }, [requestedStreamCandidateCount]);

  useEffect(() => {
    setStreamLatency(requestedStreamLatency);
  }, [requestedStreamLatency]);

  const availableModes = useMemo<readonly BufferMode[]>(() => {
    if (availablePluginIDs === undefined) return MODE_ORDER;
    const availableIDs = new Set(availablePluginIDs);
    return MODE_ORDER.filter((candidateMode) => (
      candidateMode === "normal"
      || (candidateMode === "ai"
        ? AI_CONNECTOR_ORDER.some((connector) => availableIDs.has(AI_CHANNEL_PLUGINS[connector].id))
        : availableIDs.has(BUFFER_MODE_PLUGIN_IDS[candidateMode]))
    ));
  }, [availablePluginIDs]);
  const availableAIConnectors = useMemo<readonly BufferAIConnector[]>(() => {
    if (availablePluginIDs === undefined) return AI_CONNECTOR_ORDER;
    const availableIDs = new Set(availablePluginIDs);
    return AI_CONNECTOR_ORDER.filter((connector) => availableIDs.has(AI_CHANNEL_PLUGINS[connector].id));
  }, [availablePluginIDs]);
  const availableModeSet = useMemo(() => new Set(availableModes), [availableModes]);
  const modeIsAvailable = availableModeSet.has(mode);
  const effectiveMode: BufferMode = modeIsAvailable ? mode : "normal";
  const descriptor = effectiveMode === "ai"
    ? {
      ...MODE_DESCRIPTORS.ai,
      label: aiChannelLabel(aiConnector),
      action: "请求",
      loadingAction: "请求中…",
    }
    : MODE_DESCRIPTORS[effectiveMode];
  const layout = bufferLayoutFor(effectiveMode);
  const liveExpand = layout === "live-expand";
  const exchange = layout === "single-exchange" && effectiveMode !== "normal";
  const protectedContent = phase === "protected";
  const loading = phase === "loading";
  const hasSource = sourceText.trim().length > 0;
  const showLiveTargetRail = liveExpand && !protectedContent && hasSource;
  // Exchange plugins keep one rail: write first, then swap to waiting/results.
  const exchangeDecision = exchange && !protectedContent && (loading || targets.length > 0);
  const inputContextKey = bufferInputContextKey(
    effectiveMode,
    sourceText,
    sourceLanguage,
    targetLanguage,
    streamCandidateCount,
    streamLatency,
    aiConnector,
  );
  const [internalTargetContextKey, setInternalTargetContextKey] = useState<string | null>(
    () => controlledTargets === undefined && targets.length > 0 ? inputContextKey : null,
  );
  const resolvedSelectedTarget = clampTargetIndex(selectedTarget, targets.length);
  const selectedOutput = (liveExpand || exchangeDecision)
    ? targets[resolvedSelectedTarget] ?? ""
    : sourceText;
  const sendingTargetResult = liveExpand || exchangeDecision;
  const targetMatchesManagedRequest = targetContext !== undefined
    && managedGenerationRequest !== null
    && managedGenerationRequest !== undefined
    && targetContext.requestID === managedGenerationRequest.requestID
    && targetContext.contextKey === managedGenerationRequest.contextKey;
  const targetMatchesSettledRequest = targetContext !== undefined
    && settledGenerationRequest !== null
    && targetContext.requestID === settledGenerationRequest.requestID
    && targetContext.contextKey === settledGenerationRequest.contextKey;
  const outputIsCurrent = !sendingTargetResult || (
    (controlledTargets === undefined
      ? targetsAreCurrent
        && internalTargetContextKey === inputContextKey
      : activeRequestID !== undefined
        && targetContext !== undefined
        && targetContext.requestID === activeRequestID
        && targetContext.contextKey === inputContextKey
        && (
          managedGenerationRequest === undefined
          || targetMatchesManagedRequest
          || targetMatchesSettledRequest
        ))
    && targets[resolvedSelectedTarget] !== undefined
  );
  const canSend = !paused
    && !protectedContent
    && !loading
    && !sending
    && !copying
    && (phase === "ready" || phase === "error")
    && outputIsCurrent
    && selectedOutput.trim().length > 0;
  const canRequest = !paused && !protectedContent && !loading && !sending && !copying && hasSource;
  const canCopy = sendingTargetResult
    && !paused
    && !protectedContent
    && !loading
    && !sending
    && !copying
    && outputIsCurrent
    && selectedOutput.trim().length > 0;
  const speaksBlocks = readAloud
    && effectiveMode === "translation"
    && !paused
    && !protectedContent;
  const phaseStatus = effectiveMode === "translation" && phase === "loading"
    ? "正在使用 Apple 本地翻译"
    : statusFor(effectiveMode, phase, hasSource);
  const phaseOverridesDelivery = phase === "protected"
    || phase === "loading"
    || (phase === "error" && deliveryNote.length === 0);
  const loadingStatusRenderedInRail = loading && (exchangeDecision || showLiveTargetRail);
  const status = loadingStatusRenderedInRail
    ? null
    : (phaseOverridesDelivery ? phaseStatus : deliveryNote || phaseStatus);
  const statusTone = phaseOverridesDelivery ? phase : deliveryNote ? deliveryTone : phase;
  const retainedErrorAssistiveStatus = phase === "error"
    && deliveryNote.length === 0
    && outputIsCurrent
    && targets.length > 0
    ? `处理失败，已保留上次结果。候选 ${resolvedSelectedTarget + 1} / ${targets.length}：${selectedOutput}`
    : null;
  const assistiveStatus = retainedErrorAssistiveStatus
    ?? (loadingStatusRenderedInRail ? phaseStatus : status)
    ?? (
    phase === "ready" && outputIsCurrent && targets.length > 0
      ? `已生成，候选 ${resolvedSelectedTarget + 1} / ${targets.length}：${selectedOutput}`
      : ""
  );
  const generationContext = useRef({
    mode: effectiveMode,
    paused,
    protectedContent,
    sourceText,
  });
  const lastExternalSourceRevision = useRef(externalSource?.revision);
  const resultSnapshot = useRef({ current: outputIsCurrent, count: targets.length });
  const previousInputContextKey = useRef(inputContextKey);
  const deliveryContext = useRef({
    inputContextKey,
    mode: effectiveMode,
    output: selectedOutput,
    paused,
    protectedContent,
  });
  generationContext.current = {
    mode: effectiveMode,
    paused,
    protectedContent,
    sourceText,
  };
  resultSnapshot.current = { current: outputIsCurrent, count: targets.length };
  deliveryContext.current = {
    inputContextKey,
    mode: effectiveMode,
    output: selectedOutput,
    paused,
    protectedContent,
  };
  const languageOptions = useMemo(() => {
    const values = new Set(languages.map((language) => language.value));
    const additions: BufferLanguage[] = [];
    if (!values.has(sourceLanguage)) additions.push({ value: sourceLanguage, label: sourceLanguage });
    if (!values.has(targetLanguage)) additions.push({ value: targetLanguage, label: targetLanguage });
    return [...languages, ...additions];
  }, [languages, sourceLanguage, targetLanguage]);
  const targetLanguageOptions = useMemo(
    () => languageOptions.filter((language) => language.value !== "auto"),
    [languageOptions],
  );

  const cancelGenerationWork = useCallback(() => {
    generationRevision.current += 1;
    generationAbortController.current?.abort();
    generationAbortController.current = null;
    if (generationTimer.current === undefined) return;
    window.clearTimeout(generationTimer.current);
    generationTimer.current = undefined;
  }, []);

  const cancelPendingGeneration = useCallback((preserveSettledResult = false) => {
    cancelGenerationWork();
    if (managedGenerationRequestRef.current !== undefined) {
      managedGenerationRequestRef.current = null;
      setManagedGenerationRequest(null);
    }
    if (!preserveSettledResult && settledGenerationRequestRef.current !== null) {
      settledGenerationRequestRef.current = null;
      setSettledGenerationRequest(null);
    }
  }, [cancelGenerationWork]);

  const issueGenerationRequest = useCallback((requestID: number, contextKey: string) => {
    const abortController = new AbortController();
    generationAbortController.current?.abort();
    generationAbortController.current = abortController;
    const request = { requestID, contextKey } satisfies BufferTargetContext;
    managedGenerationRequestRef.current = request;
    setManagedGenerationRequest(request);
    return { ...request, signal: abortController.signal } satisfies BufferGenerationContext;
  }, []);

  const clearDeliveryStatus = useCallback(() => {
    deliveryAbortController.current?.abort();
    deliveryAbortController.current = null;
    deliveryRevision.current += 1;
    copyRevision.current += 1;
    sendingRef.current = false;
    setSending(false);
    setCopying(false);
    setDeliveryNote("");
    setDeliveryTone("ready");
  }, []);

  const runGeneration = useCallback((requestMode: BufferMode, requestSource: string) => {
    if (paused || protectedContent) return;
    const preservesPriorResult = targets.length > 0 && outputIsCurrent;
    cancelPendingGeneration(preservesPriorResult);
    const requestRevision = generationRevision.current;
    const requestContextKey = bufferInputContextKey(
      requestMode,
      requestSource,
      sourceLanguage,
      targetLanguage,
      streamCandidateCount,
      streamLatency,
      aiConnector,
    );
    clearDeliveryStatus();
    if (!preservesPriorResult) {
      setTargetsAreCurrent(false);
      setTargets([]);
      setSelectedTarget(0);
    }
    setPhase("loading");
    onGenerateRef.current?.(
      requestMode,
      requestSource,
      issueGenerationRequest(requestRevision, requestContextKey),
    );

    if (controlledPhaseRef.current === undefined) {
      const timerID = window.setTimeout(() => {
        if (generationTimer.current === timerID) generationTimer.current = undefined;
        const currentContext = generationContext.current;
        if (
          generationRevision.current !== requestRevision
          || currentContext.mode !== requestMode
          || currentContext.paused
          || currentContext.sourceText !== requestSource
          || currentContext.protectedContent
        ) return;
        const nextTargets = generatedTargetsFor(
          requestMode,
          requestSource,
          streamCandidateCount,
          aiConnector,
        );
        setTargets(nextTargets);
        setSelectedTarget(0);
        setTargetsAreCurrent(nextTargets.length > 0);
        setInternalTargetContextKey(nextTargets.length > 0 ? requestContextKey : null);
        const firstTarget = nextTargets[0];
        if (firstTarget !== undefined) onTargetSelectRef.current?.(0, firstTarget);
        setPhase("ready");
      }, bufferLayoutFor(requestMode) === "live-expand"
        ? LIVE_GENERATION_PREVIEW_MS
        : EXCHANGE_GENERATION_PREVIEW_MS);
      generationTimer.current = timerID;
    }
  }, [
    cancelPendingGeneration,
    clearDeliveryStatus,
    issueGenerationRequest,
    paused,
    protectedContent,
    setPhase,
    setSelectedTarget,
    setTargets,
    streamCandidateCount,
    streamLatency,
    sourceLanguage,
    targetLanguage,
    targets.length,
    outputIsCurrent,
    aiConnector,
  ]);

  // Render the safe fallback immediately, then persist it through either the
  // uncontrolled state or the controlled owner's onModeChange callback.
  useEffect(() => {
    if (modeIsAvailable) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setSelectedTarget(0);
    setTargets([]);
    setMode("normal");
    if (!protectedContent) setPhase(sourceText.trim() ? "ready" : "idle");
  }, [cancelPendingGeneration, clearDeliveryStatus, mode, modeIsAvailable]);

  useEffect(() => {
    if (controlledTargets === undefined) return;
    const shouldRetireSettledResult = targets.length === 0
      || targetContext === undefined
      || targetContext.contextKey !== inputContextKey;
    if (shouldRetireSettledResult) {
      if (settledGenerationRequestRef.current !== null) {
        settledGenerationRequestRef.current = null;
        setSettledGenerationRequest(null);
      }
      return;
    }
    if (paused || protectedContent || phase !== "ready" || !outputIsCurrent) return;
    const currentSettledRequest = settledGenerationRequestRef.current;
    if (
      currentSettledRequest?.requestID === targetContext.requestID
      && currentSettledRequest.contextKey === targetContext.contextKey
    ) return;
    const nextSettledRequest = {
      requestID: targetContext.requestID,
      contextKey: targetContext.contextKey,
    } satisfies BufferTargetContext;
    settledGenerationRequestRef.current = nextSettledRequest;
    setSettledGenerationRequest(nextSettledRequest);
  }, [
    controlledTargets,
    inputContextKey,
    outputIsCurrent,
    paused,
    phase,
    protectedContent,
    targetContext,
    targets.length,
  ]);

  useEffect(() => {
    if (previousInputContextKey.current === inputContextKey) return;
    previousInputContextKey.current = inputContextKey;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
  }, [cancelPendingGeneration, clearDeliveryStatus, inputContextKey]);

  useEffect(() => {
    // Active renders routinely change phase while a debounce/preview timer is
    // live. Only the two suspension states are allowed to cancel that work;
    // otherwise this effect would invalidate the request it just scheduled.
    if (!paused && !protectedContent) return;
    cancelPendingGeneration(true);
    if (protectedContent) {
      clearDeliveryStatus();
      return;
    }
    const interruptedDelivery = sendingRef.current;
    deliveryAbortController.current?.abort();
    deliveryAbortController.current = null;
    deliveryRevision.current += 1;
    sendingRef.current = false;
    setSending(false);
    if (interruptedDelivery) {
      setDeliveryNote("发送已暂停，请确认目标状态后重试");
      setDeliveryTone("error");
    }
    if (phase === "loading") setPhase(hasSource ? "ready" : "idle");
  }, [
    cancelPendingGeneration,
    clearDeliveryStatus,
    effectiveMode,
    hasSource,
    paused,
    phase,
    protectedContent,
    setPhase,
  ]);

  useEffect(() => {
    if (phase === "loading") clearDeliveryStatus();
  }, [clearDeliveryStatus, phase]);

  // Live-expand plugins wait for a quiet typing window before asking the host.
  // Callback refs deliberately stay outside the dependency list so parent-only
  // renders (theme, notices, inspector state) cannot restart a request.
  useEffect(() => {
    if (!liveExpand) return;
    if (paused || protectedContent) return;
    if (resultSnapshot.current.current && resultSnapshot.current.count > 0) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setTargets([]);
    setSelectedTarget(0);

    if (!hasSource) {
      setPhase("idle");
      return;
    }

    if (effectiveMode === "translation" && !translationContinuously) {
      setPhase("ready");
      return;
    }

    const requestMode = effectiveMode;
    const requestSource = sourceText;
    const requestRevision = generationRevision.current;
    const requestContextKey = inputContextKey;
    const debounceDelay = effectiveMode === "stream"
      ? streamLatency === "fast" ? 260 : streamLatency === "stable" ? 620 : LIVE_GENERATION_DEBOUNCE_MS
      : LIVE_GENERATION_DEBOUNCE_MS;
    const debounceTimerID = window.setTimeout(() => {
      if (generationTimer.current === debounceTimerID) generationTimer.current = undefined;
      const currentContext = generationContext.current;
      if (
        generationRevision.current !== requestRevision
        || currentContext.mode !== requestMode
        || currentContext.paused
        || currentContext.sourceText !== requestSource
        || currentContext.protectedContent
      ) return;

      setPhase("loading");
      onGenerateRef.current?.(
        requestMode,
        requestSource,
        issueGenerationRequest(requestRevision, requestContextKey),
      );
      if (controlledPhaseRef.current !== undefined) return;

      const previewTimerID = window.setTimeout(() => {
        if (generationTimer.current === previewTimerID) generationTimer.current = undefined;
        const latestContext = generationContext.current;
        if (
          generationRevision.current !== requestRevision
          || latestContext.mode !== requestMode
          || latestContext.paused
          || latestContext.sourceText !== requestSource
          || latestContext.protectedContent
        ) return;
        const nextTargets = generatedTargetsFor(
          requestMode,
          requestSource,
          streamCandidateCount,
          aiConnector,
        );
        setTargets(nextTargets);
        setSelectedTarget(0);
        setTargetsAreCurrent(nextTargets.length > 0);
        setInternalTargetContextKey(nextTargets.length > 0 ? requestContextKey : null);
        const firstTarget = nextTargets[0];
        if (firstTarget !== undefined) onTargetSelectRef.current?.(0, firstTarget);
        setPhase("ready");
      }, LIVE_GENERATION_PREVIEW_MS);
      generationTimer.current = previewTimerID;
    }, debounceDelay);
    generationTimer.current = debounceTimerID;
  }, [
    cancelPendingGeneration,
    clearDeliveryStatus,
    effectiveMode,
    hasSource,
    liveExpand,
    inputContextKey,
    issueGenerationRequest,
    paused,
    protectedContent,
    sourceLanguage,
    sourceText,
    streamCandidateCount,
    streamLatency,
    targetLanguage,
    translationContinuously,
    aiConnector,
  ]);

  useEffect(() => () => {
    cancelGenerationWork();
    deliveryAbortController.current?.abort();
    deliveryRevision.current += 1;
    copyRevision.current += 1;
  }, [cancelGenerationWork]);

  useEffect(() => {
    if (selectedTarget === resolvedSelectedTarget) return;
    setSelectedTarget(resolvedSelectedTarget);
    const resolvedTarget = targets[resolvedSelectedTarget];
    if (resolvedTarget !== undefined) {
      onTargetSelectRef.current?.(resolvedSelectedTarget, resolvedTarget);
    }
  }, [resolvedSelectedTarget, selectedTarget, targets]);

  useEffect(() => {
    if (externalSource === undefined) return;
    if (lastExternalSourceRevision.current === externalSource.revision) return;
    lastExternalSourceRevision.current = externalSource.revision;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setTargets([]);
    setSelectedTarget(0);
    const incomingBlock = `[${externalSource.sourceLabel}] ${externalSource.text.trim()}`;
    const nextSource = sourceText.trim()
      ? `${sourceText.trimEnd()} · ${incomingBlock}`
      : incomingBlock;
    setSourceText(nextSource);
    if (!protectedContent) setPhase(nextSource.trim() ? "ready" : "idle");
  }, [
    cancelPendingGeneration,
    clearDeliveryStatus,
    externalSource,
    protectedContent,
    setPhase,
    setSelectedTarget,
    setSourceText,
    setTargets,
    sourceText,
  ]);

  const changeMode = (nextMode: BufferMode) => {
    if (paused || sending || copying) return;
    if (!availableModeSet.has(nextMode)) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setMode(nextMode);
    setTargetsAreCurrent(false);
    setSelectedTarget(0);
    setTargets([]);
    if (phase !== "protected") setPhase(sourceText.trim() ? "ready" : "idle");
  };

  const changeSourceLanguage = (event: ChangeEvent<HTMLSelectElement>) => {
    if (paused || sending || copying) return;
    const next = event.target.value;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setSourceLanguage(next);
    if (!protectedContent && liveExpand) {
      setTargets([]);
      setPhase(hasSource ? "ready" : "idle");
    }
    onLanguageChange?.(next, targetLanguage);
    onPluginConfigurationChange?.({
      mode: "translation",
      sourceLanguage: next,
      targetLanguage,
      translateContinuously: translationContinuously,
    });
  };

  const changeTargetLanguage = (event: ChangeEvent<HTMLSelectElement>) => {
    if (paused || sending || copying) return;
    const next = event.target.value;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setTargetLanguage(next);
    if (!protectedContent && liveExpand) {
      setTargets([]);
      setPhase(hasSource ? "ready" : "idle");
    }
    onLanguageChange?.(sourceLanguage, next);
    onPluginConfigurationChange?.({
      mode: "translation",
      sourceLanguage,
      targetLanguage: next,
      translateContinuously: translationContinuously,
    });
  };

  const swapLanguages = () => {
    if (paused || sending || copying) return;
    const nextSource = targetLanguage;
    const nextTarget = sourceLanguage === "auto" ? "en" : sourceLanguage;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setSourceLanguage(nextSource);
    setTargetLanguage(nextTarget);
    if (!protectedContent && liveExpand) {
      setTargets([]);
      setPhase(hasSource ? "ready" : "idle");
    }
    onLanguageChange?.(nextSource, nextTarget);
    onPluginConfigurationChange?.({
      mode: "translation",
      sourceLanguage: nextSource,
      targetLanguage: nextTarget,
      translateContinuously: translationContinuously,
    });
  };

  const changeTranslationContinuously = (continuous: boolean) => {
    if (paused || protectedContent || loading || sending || copying) return;
    cancelPendingGeneration(true);
    clearDeliveryStatus();
    setTranslationContinuously(continuous);
    onPluginConfigurationChange?.({
      mode: "translation",
      sourceLanguage,
      targetLanguage,
      translateContinuously: continuous,
    });
  };

  const changeAIConnector = (connector: BufferAIConnector) => {
    if (paused || protectedContent || loading || sending || copying) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setAIConnector(connector);
    setTargets([]);
    setSelectedTarget(0);
    setPhase(hasSource ? "ready" : "idle");
    onPluginConfigurationChange?.({ mode: "ai", connector });
  };

  const changeAIInference = (configuration: AIInferenceConfiguration) => {
    if (paused || protectedContent || sending || copying) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setTargets([]);
    setSelectedTarget(0);
    setPhase(hasSource ? "ready" : "idle");
    setLocalAIConfigurations((current) => ({ ...current, [aiConnector]: configuration }));
    onAIInferenceChange?.(aiConnector, configuration);
  };

  const changeStreamCandidateCount = (count: number) => {
    if (paused || protectedContent || loading || sending || copying) return;
    const next = Math.min(5, Math.max(1, Math.trunc(count)));
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setStreamCandidateCount(next);
    setTargets([]);
    setSelectedTarget(0);
    setPhase(hasSource ? "ready" : "idle");
    onPluginConfigurationChange?.({
      mode: "stream",
      candidateCount: next,
      latency: streamLatency,
    });
  };

  const changeStreamLatency = (latency: "fast" | "balanced" | "stable") => {
    if (paused || protectedContent || loading || sending || copying) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setStreamLatency(latency);
    setTargets([]);
    setSelectedTarget(0);
    setPhase(hasSource ? "ready" : "idle");
    onPluginConfigurationChange?.({
      mode: "stream",
      candidateCount: streamCandidateCount,
      latency,
    });
  };

  const generate = () => {
    const manualLiveRequest = liveExpand && (
      phase === "error"
      || (effectiveMode === "translation" && !translationContinuously)
    );
    if (paused || !canRequest || effectiveMode === "normal" || (liveExpand && !manualLiveRequest)) return;
    runGeneration(effectiveMode, sourceText);
  };

  const selectTarget = (index: number) => {
    if (paused || sending || copying) return;
    const nextIndex = clampTargetIndex(index, targets.length);
    const target = targets[nextIndex];
    if (target === undefined) return;
    clearDeliveryStatus();
    setSelectedTarget(nextIndex);
    onTargetSelectRef.current?.(nextIndex, target);
  };

  const send = async () => {
    if (!canSend) return;
    const requestInputContextKey = inputContextKey;
    const requestMode = effectiveMode;
    const requestOutput = selectedOutput;
    const requestRevision = deliveryRevision.current + 1;
    deliveryRevision.current = requestRevision;
    const abortController = new AbortController();
    deliveryAbortController.current?.abort();
    deliveryAbortController.current = abortController;
    sendingRef.current = true;
    setSending(true);
    setDeliveryNote("正在发送");
    setDeliveryTone("loading");

    let acknowledgement: BufferSendAcknowledgement | undefined;
    try {
      acknowledgement = await onSendRef.current?.(
        requestOutput,
        requestMode,
        abortController.signal,
      );
    } catch {
      acknowledgement = false;
    }

    const latestContext = deliveryContext.current;
    const deliveryBecameUnsafe = abortController.signal.aborted
      || latestContext.paused
      || latestContext.protectedContent
      || latestContext.inputContextKey !== requestInputContextKey
      || latestContext.mode !== requestMode
      || latestContext.output !== requestOutput;
    if (deliveryRevision.current !== requestRevision) return;
    if (deliveryBecameUnsafe) {
      abortController.abort();
      if (deliveryAbortController.current === abortController) {
        deliveryAbortController.current = null;
      }
      deliveryRevision.current += 1;
      sendingRef.current = false;
      setSending(false);
      setDeliveryNote(latestContext.paused || latestContext.protectedContent
        ? "发送已暂停，请确认目标状态后重试"
        : "发送上下文已变化，请重试");
      setDeliveryTone("error");
      return;
    }
    if (deliveryAbortController.current === abortController) {
      deliveryAbortController.current = null;
    }
    sendingRef.current = false;
    setSending(false);
    if (acknowledgement !== true) {
      setDeliveryNote("发送失败，请重试");
      setDeliveryTone("error");
      return;
    }

    setDeliveryNote("已发送");
    setDeliveryTone("ready");
    if (requestMode === "translation") speakBlock(requestOutput);
    if (exchange) {
      cancelPendingGeneration();
      setTargetsAreCurrent(false);
      setTargets([]);
      setSelectedTarget(0);
      setPhase(hasSource ? "ready" : "idle");
    }
  };

  // Read-aloud reads one block at a time, only when the switch is on and the
  // block is clicked or sent. A newer block replaces the one being read.
  const speakBlock = (text: string) => {
    if (!speaksBlocks) return;
    stopSpeech.current?.();
    const stop = speakBufferResult(text, targetLanguage, () => {
      if (stopSpeech.current === stop) stopSpeech.current = null;
    });
    if (!stop) {
      setDeliveryNote("当前环境不支持朗读");
      return;
    }
    stopSpeech.current = stop;
  };

  const changeReadAloud = (enabled: boolean) => {
    setReadAloud(enabled);
    if (!enabled) stopSpeech.current?.();
    onReadAloudChange?.(enabled);
  };

  useEffect(() => {
    if (effectiveMode !== "translation" || protectedContent || paused) stopSpeech.current?.();
  }, [effectiveMode, protectedContent, paused]);

  useEffect(() => () => stopSpeech.current?.(), []);

  const copyCurrentResult = async () => {
    if (!canCopy) return;
    const requestInputContextKey = inputContextKey;
    const requestMode = effectiveMode;
    const requestOutput = selectedOutput;
    const requestRevision = copyRevision.current + 1;
    copyRevision.current = requestRevision;
    setCopying(true);
    setDeliveryNote("正在复制");
    setDeliveryTone("loading");

    const copied = await copyBufferResult(requestOutput);
    if (copyRevision.current !== requestRevision) return;

    const latestContext = deliveryContext.current;
    const contextChanged = latestContext.paused
      || latestContext.protectedContent
      || latestContext.inputContextKey !== requestInputContextKey
      || latestContext.mode !== requestMode
      || latestContext.output !== requestOutput;
    setCopying(false);
    if (!copied) {
      setDeliveryNote("复制失败，请重试");
      setDeliveryTone("error");
      return;
    }
    if (contextChanged) {
      setDeliveryNote("原结果已复制；当前内容已变化");
      setDeliveryTone("error");
      return;
    }

    setDeliveryNote("已复制到剪贴板");
    setDeliveryTone("ready");
    onClose?.();
  };

  const returnToExchangeSource = () => {
    if (!exchange || paused || sending || copying) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setTargets([]);
    setSelectedTarget(0);
    setPhase(hasSource ? "ready" : "idle");
  };

  const retryExchangeGeneration = () => {
    if (!exchange || paused || !canRequest) return;
    runGeneration(effectiveMode, sourceText);
  };

  const awaitingExchangeRequest = exchange && !exchangeDecision;
  const awaitingLiveRequest = liveExpand && hasSource && (
    (phase === "error" && !outputIsCurrent)
    || (
      !translationContinuously
      && effectiveMode === "translation"
      && (targets.length === 0 || !outputIsCurrent)
    )
  );
  const awaitingRequest = awaitingExchangeRequest || awaitingLiveRequest;
  const primaryAction = awaitingRequest ? generate : send;
  const primaryLabel = sending
    ? "发送中…"
    : loading
    ? descriptor.loadingAction
    : awaitingRequest
      ? descriptor.action
      : "发送";
  const primaryIcon: IconName = awaitingRequest
      ? descriptor.icon
      : "send";

  const onSourceEdit = (text: string) => {
    if (paused || sending || copying) return;
    cancelPendingGeneration();
    clearDeliveryStatus();
    setTargetsAreCurrent(false);
    setSourceText(text);
    if (!protectedContent && (exchange || liveExpand) && targets.length > 0) {
      // Editing source abandons the previous result set before another request.
      setTargets([]);
    }
    if (!protectedContent) {
      setPhase(text.trim() ? "ready" : "idle");
    }
  };

  const workbenchClass = [
    "buffer-workbench",
    showLiveTargetRail ? "buffer-workbench--live-expand" : "",
    exchangeDecision ? "buffer-workbench--exchange-decision" : "",
  ].filter(Boolean).join(" ");
  const canReturnToSource = exchange
    && !loading
    && !paused
    && !sending
    && !copying
    && targets.length > 0;
  const canRetry = (
    (exchange && !loading && targets.length > 0)
    || (liveExpand && phase === "error" && outputIsCurrent && targets.length > 0)
  ) && !paused && canRequest;
  const activePluginID = effectiveMode === "normal"
    ? undefined
    : bufferPluginIDFor(effectiveMode, aiConnector);
  const inputControl = (
    <BufferInputControl
      aiConnector={aiConnector}
      aiConfiguration={aiConfiguration}
      availableModes={availableModes}
      availableAIConnectors={availableAIConnectors}
      canRetry={canRetry}
      canReturnToSource={canReturnToSource}
      configurationDisabled={paused || protectedContent || loading || sending || copying}
      descriptor={descriptor}
      languageOptions={languageOptions}
      mode={effectiveMode}
      onAIConnectorChange={changeAIConnector}
      onAIInferenceChange={changeAIInference}
      onClose={onClose}
      onModeChange={changeMode}
      onOpenSettings={activePluginID && onOpenPluginSettings
        ? () => onOpenPluginSettings(activePluginID)
        : undefined}
      onRetry={exchange ? retryExchangeGeneration : generate}
      onReturnToSource={returnToExchangeSource}
      onSourceLanguageChange={changeSourceLanguage}
      onStreamCandidateCountChange={changeStreamCandidateCount}
      onStreamLatencyChange={changeStreamLatency}
      onSwapLanguages={swapLanguages}
      onTargetLanguageChange={changeTargetLanguage}
      onTranslationContinuouslyChange={changeTranslationContinuously}
      onReadAloudChange={changeReadAloud}
      protectedContent={protectedContent}
      sourceLanguage={sourceLanguage}
      streamCandidateCount={streamCandidateCount}
      streamLatency={streamLatency}
      targetLanguage={targetLanguage}
      targetLanguageOptions={targetLanguageOptions}
      translationContinuously={translationContinuously}
      readAloud={readAloud}
    />
  );

  return (
    <section
      aria-label="缓冲工作台"
      className={`buffer-surface buffer-surface--${effectiveMode} is-toolbar-expanded${className ? ` ${className}` : ""}`}
      data-base-height={showLiveTargetRail ? 112 : 78}
      data-layout={layout}
      data-mode={effectiveMode}
      data-phase={phase}
    >
      <span aria-atomic="true" aria-live="polite" className="sr-only">
        {assistiveStatus}
      </span>

      <div className={workbenchClass}>
        <div className="buffer-workbench__rails">
          {exchangeDecision ? (
            <BufferTrack
              copyDisabled={!canCopy}
              emptyLabel="等待返回结果"
              kind="target"
              leadingControl={inputControl}
              loading={loading}
              loadingLabel={phaseStatus ?? undefined}
              thinkingText={loading ? thinkingText : undefined}
              interactionDisabled={paused || sending || copying}
              onCopy={copyCurrentResult}
              onTargetSelect={selectTarget}
              protectedContent={protectedContent}
              selectedTarget={resolvedSelectedTarget}
              status={status}
              statusTone={statusTone}
              targets={targets}
            />
          ) : (
            <BufferTrack
              kind="source"
              leadingControl={inputControl}
              loading={false}
              interactionDisabled={paused || sending || copying}
              onSourceChange={onSourceEdit}
              protectedContent={protectedContent}
              sourceValue={sourceText}
              status={showLiveTargetRail ? null : status}
              statusTone={statusTone}
            />
          )}
          {showLiveTargetRail ? (
            <BufferTrack
              copyDisabled={!canCopy}
              emptyLabel={
                effectiveMode === "stream"
                  ? "等待推测结果"
                  : "等待译文"
              }
              kind="target"
              loading={loading}
              loadingLabel={phaseStatus ?? undefined}
              thinkingText={loading ? thinkingText : undefined}
              interactionDisabled={paused || sending || copying}
              onCopy={copyCurrentResult}
              onTargetSpeak={speaksBlocks ? speakBlock : undefined}
              onTargetSelect={selectTarget}
              protectedContent={protectedContent}
              selectedTarget={resolvedSelectedTarget}
              status={status}
              statusTone={statusTone}
              targets={targets}
            />
          ) : null}
        </div>

        <IconButton
          aria-busy={loading || sending || copying}
          className={`buffer-workbench__primary-action${effectiveMode === "normal" ? "" : " is-accented"}`}
          disabled={
            protectedContent
            || paused
            || loading
            || copying
            || (primaryAction === send && !canSend)
            || (primaryAction === generate && !canRequest)
          }
          icon={primaryIcon}
          label={primaryLabel}
          onClick={primaryAction}
        />
      </div>
    </section>
  );
}
