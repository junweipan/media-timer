import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  AppState,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StatusBar as NativeStatusBar,
  StyleSheet,
  Switch,
  Text,
  ToastAndroid,
  View,
} from 'react-native';
import MediaTimer, { type TimerState } from './modules/media-timer';

const PRESETS = [10, 20, 30, 45, 60, 90];
const MIN_MINUTES = 1;
const MAX_MINUTES = 600;
const MINUTE = 60_000;

const colors = {
  background: '#0E1116',
  surface: '#181D25',
  surfaceActive: '#23304A',
  border: '#2A313C',
  text: '#F2F4F7',
  muted: '#8B95A5',
  accent: '#5B9DFF',
  danger: '#FF6B6B',
};

function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

function formatClock(time: number): string {
  return new Date(time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function clampMinutes(value: number): number {
  return Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, Math.round(value)));
}

async function ensureNotificationPermission() {
  if (Platform.OS !== 'android' || Platform.Version < 33) return;
  // The timer still works without it; only the countdown notification is hidden.
  await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
}

export default function App() {
  const insets = useSystemBarInsets();
  return (
    <View style={[styles.screen, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="light" />
      <TimerScreen />
    </View>
  );
}

/** The app draws under the status and navigation bars, so the screen is padded by their size. */
function useSystemBarInsets() {
  const [insets, setInsets] = useState(() => MediaTimer.getSystemBarInsets());

  useEffect(() => {
    // The window may not be attached on the first render; read again once it is.
    const read = () => setInsets(MediaTimer.getSystemBarInsets());
    const timeout = setTimeout(read, 300);
    const sub = AppState.addEventListener('change', read);
    return () => {
      clearTimeout(timeout);
      sub.remove();
    };
  }, []);

  return {
    top: insets.top || NativeStatusBar.currentHeight || 0,
    bottom: insets.bottom,
  };
}

function TimerScreen() {
  const [timer, setTimer] = useState<TimerState>(() => MediaTimer.getState());
  const [minutes, setMinutes] = useState(() =>
    clampMinutes(MediaTimer.getState().lastDurationMs / MINUTE)
  );
  const [canGoHome, setCanGoHome] = useState(() => MediaTimer.canGoHome());
  const [now, setNow] = useState(() => Date.now());

  const refresh = useCallback(() => {
    setTimer(MediaTimer.getState());
    setCanGoHome(MediaTimer.canGoHome());
    setNow(Date.now());
  }, []);

  // Follow changes made by the service: time up, or "+5 min" / "Cancel" on the notification.
  useEffect(() => {
    const timerSub = MediaTimer.addListener('onTimerChange', (state) => {
      setTimer(state);
      setNow(Date.now());
    });
    const appSub = AppState.addEventListener('change', (status) => {
      if (status === 'active') refresh();
    });
    return () => {
      timerSub.remove();
      appSub.remove();
    };
  }, [refresh]);

  const endTime = timer.endTime;
  const running = endTime !== null && endTime > now;

  useEffect(() => {
    if (endTime === null) return;
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [endTime]);

  const start = async () => {
    await ensureNotificationPermission();
    MediaTimer.start(minutes * MINUTE);
    refresh();
  };

  const toggleGoHome = (enabled: boolean) => {
    MediaTimer.setGoHome(enabled);
    refresh();
    if (enabled && !MediaTimer.canGoHome()) {
      Alert.alert(
        'Permission needed',
        'To leave the playing app when time is up, allow Media Timer to "Display over other apps" on the next screen. Android requires this before an app can switch screens from the background.',
        [
          { text: 'Not now', style: 'cancel' },
          { text: 'Open settings', onPress: () => MediaTimer.openGoHomeSettings() },
        ]
      );
    }
  };

  const stopNow = () => {
    MediaTimer.stopMediaNow();
    ToastAndroid.show('Sent pause to playing media', ToastAndroid.SHORT);
  };

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.title}>Media Timer</Text>
      <Text style={styles.subtitle}>
        Pauses music and video in any app when the time is up.
      </Text>

      {running ? (
        <View style={styles.panel}>
          <Text style={styles.countdown}>{formatRemaining(endTime - now)}</Text>
          <Text style={styles.caption}>Media stops at {formatClock(endTime)}</Text>

          <View style={styles.row}>
            <Button label="+5 min" onPress={() => MediaTimer.extend(5 * MINUTE)} />
            <Button label="+10 min" onPress={() => MediaTimer.extend(10 * MINUTE)} />
          </View>
          <Button label="Cancel timer" kind="danger" onPress={() => MediaTimer.cancel()} />
        </View>
      ) : (
        <View style={styles.panel}>
          <View style={styles.stepper}>
            <StepButton label="−5" onPress={() => setMinutes((m) => clampMinutes(m - 5))} />
            <StepButton label="−1" onPress={() => setMinutes((m) => clampMinutes(m - 1))} />
            <View style={styles.minutes}>
              <Text style={styles.minutesValue}>{minutes}</Text>
              <Text style={styles.caption}>min</Text>
            </View>
            <StepButton label="+1" onPress={() => setMinutes((m) => clampMinutes(m + 1))} />
            <StepButton label="+5" onPress={() => setMinutes((m) => clampMinutes(m + 5))} />
          </View>

          <View style={styles.presets}>
            {PRESETS.map((preset) => (
              <Pressable
                key={preset}
                onPress={() => setMinutes(preset)}
                style={[styles.chip, preset === minutes && styles.chipActive]}
              >
                <Text style={[styles.chipText, preset === minutes && styles.chipTextActive]}>
                  {preset} min
                </Text>
              </Pressable>
            ))}
          </View>

          <Button label={`Start ${minutes} min timer`} kind="primary" onPress={start} />
        </View>
      )}

      <View style={styles.setting}>
        <View style={styles.settingText}>
          <Text style={styles.settingTitle}>Go to home screen when time is up</Text>
          <Text style={styles.settingHint}>
            {timer.goHome && !canGoHome
              ? 'Needs the "Display over other apps" permission.'
              : 'Leaves the playing app so video is no longer on screen.'}
          </Text>
        </View>
        <Switch
          value={timer.goHome}
          onValueChange={toggleGoHome}
          trackColor={{ true: colors.accent, false: colors.border }}
          thumbColor={colors.text}
        />
      </View>
      {timer.goHome && !canGoHome && (
        <Button label="Grant permission" onPress={() => MediaTimer.openGoHomeSettings()} />
      )}

      <Pressable onPress={stopNow} style={styles.link}>
        <Text style={styles.linkText}>Pause media now (test)</Text>
      </Pressable>
    </ScrollView>
  );
}

type ButtonProps = {
  label: string;
  onPress: () => void;
  kind?: 'default' | 'primary' | 'danger';
};

function Button({ label, onPress, kind = 'default' }: ButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        kind === 'default' && styles.buttonInRow,
        kind === 'primary' && styles.buttonPrimary,
        kind === 'danger' && styles.buttonDanger,
        pressed && styles.pressed,
      ]}
    >
      <Text
        style={[
          styles.buttonText,
          kind === 'primary' && styles.buttonTextPrimary,
          kind === 'danger' && styles.buttonTextDanger,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

function StepButton({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.step, pressed && styles.pressed]}>
      <Text style={styles.stepText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: 20,
    gap: 16,
  },
  title: {
    color: colors.text,
    fontSize: 28,
    fontWeight: '700',
    marginTop: 8,
  },
  subtitle: {
    color: colors.muted,
    fontSize: 15,
    lineHeight: 21,
  },
  panel: {
    backgroundColor: colors.surface,
    borderRadius: 20,
    padding: 20,
    gap: 16,
    alignItems: 'stretch',
  },
  countdown: {
    color: colors.text,
    fontSize: 72,
    fontWeight: '200',
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
  },
  caption: {
    color: colors.muted,
    fontSize: 14,
    textAlign: 'center',
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  stepper: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  minutes: {
    alignItems: 'center',
    minWidth: 96,
  },
  minutesValue: {
    color: colors.text,
    fontSize: 64,
    fontWeight: '200',
    fontVariant: ['tabular-nums'],
  },
  step: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '600',
  },
  presets: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    justifyContent: 'center',
  },
  chip: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
  },
  chipActive: {
    backgroundColor: colors.surfaceActive,
    borderColor: colors.accent,
  },
  chipText: {
    color: colors.muted,
    fontSize: 15,
    fontWeight: '600',
  },
  chipTextActive: {
    color: colors.text,
  },
  button: {
    minHeight: 52,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  buttonInRow: {
    flexGrow: 1,
  },
  buttonPrimary: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  buttonDanger: {
    borderColor: colors.danger,
  },
  buttonText: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '600',
  },
  buttonTextPrimary: {
    color: '#08111F',
  },
  buttonTextDanger: {
    color: colors.danger,
  },
  pressed: {
    opacity: 0.6,
  },
  setting: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.surface,
    borderRadius: 16,
    padding: 16,
  },
  settingText: {
    flex: 1,
    gap: 4,
  },
  settingTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '600',
  },
  settingHint: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 18,
  },
  link: {
    alignSelf: 'center',
    padding: 12,
  },
  linkText: {
    color: colors.accent,
    fontSize: 15,
    fontWeight: '600',
  },
});
