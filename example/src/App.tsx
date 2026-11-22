import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Platform,
  ScrollView,
  Settings,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  AntNexo,
  AntNexoSheet,
  type AntNexoEvent,
  type FeedbackTheme,
} from '@antnexo/react-native';

/**
 * Launch options for scripted checks: iOS launch arguments (`-scenario show -host http://…`), Android intent extras
 * (`--es scenario show`) passed as initial props.
 */
type Launch = {
  host?: string;
  workspace?: string;
  identify?: string;
  scenario?: string;
};

function launchOption(props: Launch, k: keyof Launch): string | undefined {
  if (props[k]) return props[k];
  if (Platform.OS === 'ios') {
    const v = Settings.get(k);
    return typeof v === 'string' && v ? v : undefined;
  }
  return undefined;
}

const THEMES: FeedbackTheme[] = ['auto', 'light', 'dark'];

export default function App(props: Launch) {
  const host = launchOption(props, 'host') ?? 'https://splynx.antnexo.com';
  const workspace = launchOption(props, 'workspace') ?? 'splynx';
  const [theme, setTheme] = useState<FeedbackTheme>('auto');
  const [token, setToken] = useState(launchOption(props, 'identify') ?? '');
  const [badge, setBadge] = useState<number | null>(null);
  const [sheet, setSheet] = useState(false);
  const [ready, setReady] = useState(false);
  const [log, setLog] = useState<string[]>([]);

  const note = useCallback((line: string) => {
    console.log(`[example] ${line}`);
    setLog((l) => [line, ...l].slice(0, 30));
  }, []);

  const refreshBadge = useCallback(() => {
    AntNexo.unreadCount()
      .then(setBadge)
      .catch((e) => note(`unreadCount: ${e.code}`));
  }, [note]);

  // configure (again on a theme change)
  useEffect(() => {
    AntNexo.configure({ host, workspace, theme })
      .then(() => setReady(true))
      .catch((e) => note(`configure: ${e.code}`));
  }, [host, workspace, theme, note]);

  // the pasted identify token (the example has no backend; never put the identify secret in an app)
  useEffect(() => {
    AntNexo.identify(token ? async () => token : null);
  }, [token]);

  useEffect(() => {
    const sub = AntNexo.addListener((e: AntNexoEvent) => {
      note(`event ${JSON.stringify(e)}`);
      if (e.type === 'unread') setBadge(e.count);
    });
    return () => sub.remove();
  }, [note]);

  useEffect(() => {
    if (ready) refreshBadge();
  }, [ready, refreshBadge]);

  const scenario = launchOption(props, 'scenario');
  useEffect(() => {
    if (!ready || !scenario) return;
    note(`scenario ${scenario}`);
    if (scenario === 'show') AntNexo.show().then(() => note('show resolved'));
    if (scenario === 'roadmap') AntNexo.show({ tab: 'roadmap' });
    if (scenario === 'screenshot') AntNexo.show({ screenshot: true });
    if (scenario === 'sheet') setSheet(true);
    // runs once per launch
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  const show = (o?: Parameters<typeof AntNexo.show>[0]) =>
    AntNexo.show(o)
      .then(() => note('show resolved'))
      .catch((e) => note(`show: ${e.code}`));

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>AntNexo example</Text>
      <Text style={styles.small}>
        {host} · {workspace}
      </Text>

      <Button title="Send feedback" onPress={() => show()} />
      <Button title="Roadmap" onPress={() => show({ tab: 'roadmap' })} />
      <Button
        title="Feedback with screenshot"
        onPress={() => show({ screenshot: true })}
      />
      <Button
        title={`What's new${badge ? ` (${badge})` : ''}`}
        onPress={() => show({ tab: 'changelog' }).then(refreshBadge)}
      />

      <View style={styles.row}>
        <Text>AntNexoSheet</Text>
        <Switch value={sheet} onValueChange={setSheet} />
      </View>
      <AntNexoSheet
        visible={sheet}
        onDismiss={() => {
          note('AntNexoSheet dismissed');
          setSheet(false);
        }}
      />

      <View style={styles.row}>
        {THEMES.map((t) => (
          <Button
            key={t}
            title={t === theme ? `[${t}]` : t}
            onPress={() => setTheme(t)}
          />
        ))}
      </View>

      <TextInput
        style={styles.input}
        placeholder="Identify token (paste one signed by your backend)"
        value={token}
        onChangeText={setToken}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <Button
        title="Log out"
        onPress={() => {
          setToken('');
          AntNexo.identify(null);
          AntNexo.logout().then(() => note('logged out'));
        }}
      />

      <Text style={styles.small}>Events</Text>
      {log.map((l, i) => (
        <Text key={i} style={styles.log}>
          {l}
        </Text>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 24, paddingTop: 72, gap: 8 },
  title: { fontSize: 22, fontWeight: '600' },
  small: { fontSize: 12, opacity: 0.6 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 8 },
  log: {
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 11,
  },
});
