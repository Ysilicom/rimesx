import React from "react";
import {
  AbsoluteFill,
  Easing,
  Img,
  interpolate,
  Sequence,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

// RIMES iOS keyboard promo: 45 s, 1080×1920. Keyboard shots are real renders of the
// keyboard (see testCapturePromoFootage); chat bubbles show what gets sent.

const FONT = '"PingFang SC", "Hiragino Sans GB", "Helvetica Neue", sans-serif';
const TEAL = "#2ec4cf";
const ease = Easing.bezier(0.16, 1, 0.3, 1);

const fade = (frame: number, length: number, edge = 10) =>
  interpolate(frame, [0, edge, length - edge, length], [0, 1, 1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

// Scene timing (frames at 30 fps).
export const SCENES = {
  intro: [0, 90],
  pinyin: [90, 120],
  chord: [210, 120],
  buffer: [330, 180],
  translate: [510, 150],
  poem: [660, 135],
  art: [795, 120],
  ask: [915, 150],
  pets: [1065, 135],
  outro: [1200, 150],
} as const;
export const DURATION = 1350;

const Background: React.FC = () => {
  const frame = useCurrentFrame();
  const drift = Math.sin(frame / 90) * 60;
  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(circle at ${50 + drift / 20}% ${18 + drift / 30}%, #1c5a60 0%, #0d2629 42%, #081416 100%)`,
      }}
    />
  );
};

const Sprite: React.FC<{ name: string; count: number; fps?: number; size: number; style?: React.CSSProperties }> = ({
  name,
  count,
  fps = 10,
  size,
  style,
}) => {
  const frame = useCurrentFrame();
  const index = Math.floor((frame * fps) / 30) % count;
  return (
    <Img
      src={staticFile(`pets/${name}/f${String(index + 1).padStart(3, "0")}.png`)}
      style={{ width: size, height: size, imageRendering: "auto", ...style }}
    />
  );
};

const Caption: React.FC<{ zh: string; en: string; step?: string; length: number }> = ({ zh, en, step, length }) => {
  const frame = useCurrentFrame();
  const opacity = fade(frame, length, 12);
  const rise = interpolate(frame, [0, 18], [40, 0], { extrapolateRight: "clamp", easing: ease });
  return (
    <div
      style={{
        position: "absolute",
        top: 120,
        left: 80,
        right: 80,
        textAlign: "center",
        opacity,
        transform: `translateY(${rise}px)`,
        fontFamily: FONT,
      }}
    >
      {step ? (
        <div style={{ color: TEAL, fontSize: 30, fontWeight: 600, letterSpacing: 6, marginBottom: 18 }}>{step}</div>
      ) : null}
      <div style={{ color: "white", fontSize: 76, fontWeight: 700, lineHeight: 1.15 }}>{zh}</div>
      <div style={{ color: "#a9d6d9", fontSize: 34, marginTop: 18, lineHeight: 1.3 }}>{en}</div>
    </div>
  );
};

type Bubble = { text: string; mine: boolean; at: number; mono?: boolean };

// The chat above the keyboard, for the whole phone section (global frame numbers).
const bubblesAt = (frame: number): Bubble[] => {
  const all: Bubble[] = [
    { text: "周末有什么安排？", mine: false, at: 90 },
    { text: "今天天气很好，", mine: true, at: 420 },
    { text: "The weather is nice today, let's go to the park.", mine: true, at: 610 },
    { text: "春风拂面柳丝长，\n眠鸥听雨梦潇湘。\n不问归期何处是，\n觉来花影满东窗。", mine: true, at: 750 },
    {
      text: ["⬜🟥🟥⬜⬜🟥🟥⬜", "🟥🟥🟥🟥🟥🟥🟥🟥", "🟥🟥🟥🟥🟥🟥🟥🟥", "⬜🟥🟥🟥🟥🟥🟥⬜", "⬜⬜🟥🟥🟥🟥⬜⬜", "⬜⬜⬜🟥🟥⬜⬜⬜"].join("\n"),
      mine: true,
      at: 865,
      mono: true,
    },
    { text: "为什么天空是蓝色的？", mine: false, at: 925 },
  ];
  return all.filter((b) => frame >= b.at).slice(-3);
};

const Chat: React.FC<{ globalFrame: number; bottom: number }> = ({ globalFrame, bottom }) => {
  const { fps } = useVideoConfig();
  const bubbles = bubblesAt(globalFrame);
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        top: 150,
        bottom,
        display: "flex",
        flexDirection: "column",
        justifyContent: "flex-end",
        gap: 16,
        padding: "0 28px 20px",
        fontFamily: FONT,
      }}
    >
      {bubbles.map((b) => {
        const pop = spring({ frame: globalFrame - b.at, fps, config: { damping: 14, mass: 0.6 } });
        return (
          <div
            key={b.at}
            style={{
              alignSelf: b.mine ? "flex-end" : "flex-start",
              maxWidth: "78%",
              background: b.mine ? "#2fb6c1" : "white",
              color: b.mine ? "white" : "#111",
              borderRadius: 28,
              padding: b.mono ? "16px 18px" : "16px 24px",
              fontSize: b.mono ? 30 : 30,
              lineHeight: b.mono ? 1.05 : 1.35,
              whiteSpace: "pre-wrap",
              transform: `scale(${0.6 + 0.4 * pop})`,
              transformOrigin: b.mine ? "right bottom" : "left bottom",
              opacity: pop,
              boxShadow: "0 2px 6px rgba(0,0,0,0.08)",
            }}
          >
            {b.text}
          </div>
        );
      })}
    </div>
  );
};

// Which real keyboard render shows at a global frame.
const keyboardAt = (frame: number): { src: string; height: number } => {
  const kb = (name: string, height = 960) => ({ src: `kb/${name}.png`, height });
  if (frame < 210) return kb("01-pinyin", 720);
  if (frame < 330) return kb("02-chord", 597);
  if (frame < 420) return kb("04-buffer-typed");
  if (frame < 510) return kb("04-buffer-sent");
  if (frame < 590) return kb("05-translate");
  if (frame < 660) return kb("05-translate-settings");
  if (frame < 795) return kb("06-poem");
  if (frame < 915) return kb("07-art");
  const f = frame - 915;
  if (f < 18) return kb("08-ask-waiting");
  if (f < 52) return kb("08-ask-thinking");
  if (f < 124) return kb(`08-ask-stream-${String(Math.min(35, Math.floor((f - 52) / 2))).padStart(3, "0")}`);
  return kb("08-ask-done");
};

const PHONE = { width: 820, height: 1380, top: 470 };
const SCREEN_W = PHONE.width - 36;

const Phone: React.FC = () => {
  const frame = useCurrentFrame(); // global: this sequence starts at 0
  const start = SCENES.pinyin[0];
  const end = SCENES.pets[0];
  const enter = spring({ frame: frame - start, fps: 30, config: { damping: 16 } });
  const leave = interpolate(frame, [end - 14, end], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  if (frame < start - 2 || frame > end) return null;
  const board = keyboardAt(frame);
  const scale = SCREEN_W / 1179;
  const boardHeight = board.height * scale;
  // Gentle push-in on the keyboard while each feature plays.
  return (
    <div
      style={{
        position: "absolute",
        left: (1080 - PHONE.width) / 2,
        top: PHONE.top + (1 - enter) * 500 + leave * 700,
        width: PHONE.width,
        height: PHONE.height,
        borderRadius: 110,
        background: "#0a0a0a",
        padding: 18,
        boxShadow: "0 40px 120px rgba(0,0,0,0.55), 0 0 0 3px #2a2a2a inset",
        opacity: 1 - leave,
      }}
    >
      <div style={{ position: "relative", width: "100%", height: "100%", borderRadius: 94, overflow: "hidden", background: "#eef1f3" }}>
        {/* status bar + chat header */}
        <div style={{ position: "absolute", top: 28, left: 64, right: 64, display: "flex", justifyContent: "space-between", fontFamily: FONT, fontSize: 28, fontWeight: 600, color: "#111" }}>
          <span>9:41</span>
          <span>●●● ◔</span>
        </div>
        <div style={{ position: "absolute", top: 86, left: 0, right: 0, textAlign: "center", fontFamily: FONT, fontSize: 30, fontWeight: 600, color: "#111" }}>
          小林
        </div>
        <div style={{ position: "absolute", top: 138, left: 0, right: 0, height: 1, background: "rgba(0,0,0,0.08)" }} />
        <Chat globalFrame={frame} bottom={boardHeight} />
        <Img
          src={staticFile(board.src)}
          style={{ position: "absolute", left: 0, bottom: 0, width: SCREEN_W, height: boardHeight }}
        />
      </div>
    </div>
  );
};

const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const logo = spring({ frame, fps, config: { damping: 12 } });
  const opacity = fade(frame, SCENES.intro[1], 10);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity, fontFamily: FONT }}>
      <Sprite name="rhino-walk" count={9} size={300} style={{ transform: `translateX(${interpolate(frame, [0, 60], [-260, 0], { extrapolateRight: "clamp", easing: ease })}px)` }} />
      <div style={{ color: "white", fontSize: 150, fontWeight: 800, letterSpacing: 10, transform: `scale(${0.7 + 0.3 * logo})`, marginTop: 20 }}>RIMES</div>
      <div style={{ color: TEAL, fontSize: 44, fontWeight: 600, marginTop: 10 }}>把想法，写得顺一点</div>
      <div style={{ color: "#a9d6d9", fontSize: 32, marginTop: 14 }}>A little more flow, in every word.</div>
    </AbsoluteFill>
  );
};

const PET_ROW: { name: string; count: number; fps: number }[] = [
  { name: "rhino-run", count: 18, fps: 14 },
  { name: "noto-1f415", count: 36, fps: 16 },
  { name: "noto-1f416", count: 51, fps: 16 },
  { name: "noto-1f98a", count: 32, fps: 16 },
  { name: "noto-1f43c", count: 39, fps: 16 },
  { name: "noto-1f427", count: 50, fps: 16 },
];
const DRAWN = ["crab-ready", "kitten-ready", "puppy-ready", "piglet-ready"];

const Pets: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = fade(frame, SCENES.pets[1], 10);
  return (
    <AbsoluteFill style={{ opacity }}>
      <div style={{ position: "absolute", top: 560, left: 80, right: 80, display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 36 }}>
        {PET_ROW.map((p, i) => {
          const pop = spring({ frame: frame - 6 - i * 4, fps, config: { damping: 12 } });
          return (
            <div key={p.name} style={{ width: 260, height: 260, borderRadius: 60, background: "rgba(255,255,255,0.08)", display: "flex", alignItems: "center", justifyContent: "center", transform: `scale(${pop})` }}>
              <Sprite name={p.name} count={p.count} fps={p.fps} size={200} />
            </div>
          );
        })}
        {DRAWN.map((d, i) => {
          const pop = spring({ frame: frame - 30 - i * 4, fps, config: { damping: 12 } });
          const bob = Math.sin((frame + i * 9) / 5) * 6;
          return (
            <div key={d} style={{ width: 190, height: 190, borderRadius: 48, background: "rgba(255,255,255,0.08)", display: "flex", alignItems: "center", justifyContent: "center", transform: `scale(${pop}) translateY(${bob}px)` }}>
              <Img src={staticFile(`kb/09-pet-${d}.png`)} style={{ width: 150, height: 165 }} />
            </div>
          );
        })}
      </div>
      <div style={{ position: "absolute", bottom: 150, left: 0, right: 0, textAlign: "center", fontFamily: FONT, color: "#a9d6d9", fontSize: 32 }}>
        等待 · 输出 · 完成 · 出错，一眼就懂 — Waiting, streaming, done, error: at a glance
      </div>
    </AbsoluteFill>
  );
};

const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = interpolate(frame, [0, 12], [0, 1], { extrapolateRight: "clamp" });
  const sig = spring({ frame: frame - 10, fps, config: { damping: 14 } });
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity, fontFamily: FONT }}>
      <div style={{ background: "white", borderRadius: 32, padding: "26px 34px", maxWidth: 860, fontSize: 30, lineHeight: 1.5, color: "#222", transform: `scale(${sig})`, whiteSpace: "pre-wrap", boxShadow: "0 20px 60px rgba(0,0,0,0.35)" }}>
        {"—\n共 356 字 · 平均 62 字/分 · 码长 1.82 触/字\n来自 RIMES 免费开源输入法"}
      </div>
      <div style={{ color: "#a9d6d9", fontSize: 28, marginTop: 18 }}>轻点打字速度，附上你的打字签名 · Tap your speed to sign it</div>
      <Sprite name="rhino-cheer" count={7} fps={10} size={260} style={{ marginTop: 50 }} />
      <div style={{ color: "white", fontSize: 110, fontWeight: 800, letterSpacing: 8, marginTop: 10 }}>RIMES</div>
      <div style={{ color: TEAL, fontSize: 40, fontWeight: 600, marginTop: 8 }}>免费 · 开源 · 离线优先</div>
      <div style={{ color: "#a9d6d9", fontSize: 30, marginTop: 12 }}>Free & open source · Offline first · Your keys stay on your iPhone</div>
    </AbsoluteFill>
  );
};

const CAPTIONS: Record<string, { zh: string; en: string; step: string }> = {
  pinyin: { zh: "离线中文输入", en: "Offline Chinese input: Pinyin, Ziranma, Wubi", step: "01" },
  chord: { zh: "滑动并击", en: "Slide chords: two thumbs, one syllable, live preview", step: "02" },
  buffer: { zh: "先写后发，逐块上屏", en: "Write first, send block by block, with live typing speed", step: "03" },
  translate: { zh: "实时翻译，分块输出", en: "Live translation in blocks. Tap a block to hear it", step: "04" },
  poem: { zh: "AI 作诗", en: "AI Poem: acrostics, patterns & word cards", step: "05" },
  art: { zh: "AI 字符画", en: "AI Text Art that keeps its frame, row by row", step: "06" },
  ask: { zh: "快问快答", en: "Quick Q&A: readable thinking, answers that flow", step: "07" },
  pets: { zh: "会动的状态灯", en: "A status light that comes alive: the RIMES rhino & friends", step: "08" },
};

export const Promo: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: "#081416" }}>
      <Background />
      <Sequence durationInFrames={SCENES.intro[1]}>
        <Intro />
      </Sequence>
      {Object.entries(CAPTIONS).map(([key, c]) => {
        const [from, length] = SCENES[key as keyof typeof SCENES];
        return (
          <Sequence key={key} from={from} durationInFrames={length} layout="none">
            <Caption zh={c.zh} en={c.en} step={c.step} length={length} />
          </Sequence>
        );
      })}
      <Phone />
      <Sequence from={SCENES.pets[0]} durationInFrames={SCENES.pets[1]}>
        <Pets />
      </Sequence>
      <Sequence from={SCENES.outro[0]} durationInFrames={SCENES.outro[1]}>
        <Outro />
      </Sequence>
    </AbsoluteFill>
  );
};
