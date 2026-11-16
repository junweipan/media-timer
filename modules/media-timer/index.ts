import { NativeModule, requireNativeModule } from 'expo';

export type TimerState = {
  /** Time the timer ends, in ms since epoch. null when no timer is running. */
  endTime: number | null;
  /** Whether the phone goes to the home screen when time is up. */
  goHome: boolean;
  /** Duration of the last timer that was started, in ms. */
  lastDurationMs: number;
};

type MediaTimerEvents = {
  onTimerChange: (state: TimerState) => void;
};

declare class MediaTimerModule extends NativeModule<MediaTimerEvents> {
  getState(): TimerState;
  start(durationMs: number): void;
  extend(durationMs: number): void;
  cancel(): void;
  setGoHome(enabled: boolean): void;
  /** Pauses media in other apps immediately. */
  stopMediaNow(): void;
  /** Status bar and navigation bar sizes in dp. Zero until the window is attached. */
  getSystemBarInsets(): { top: number; bottom: number };
  /** True when "Display over other apps" is granted, which going home needs. */
  canGoHome(): boolean;
  openGoHomeSettings(): void;
}

export default requireNativeModule<MediaTimerModule>('MediaTimer');
