import "./index.css";
import { Composition } from "remotion";
import { DURATION, Promo } from "./Composition";

export const RemotionRoot: React.FC = () => {
  return (
    <Composition id="RimesPromo" component={Promo} durationInFrames={DURATION} fps={30} width={1080} height={1920} />
  );
};
