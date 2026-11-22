import { useEffect } from 'react';
import { Button, Platform, Settings, StyleSheet, View } from 'react-native';
import { AntNexo } from '@antnexo/react-native';

/** iOS launch arguments (`-scenario show`) for scripted simulator checks. */
const arg = (k: string): string | undefined => (Platform.OS === 'ios' ? Settings.get(k) : undefined);

export default function App() {
  useEffect(() => {
    AntNexo.configure({ host: 'https://splynx.antnexo.com', workspace: 'splynx' })
      .then(() => {
        if (arg('scenario') === 'show') return AntNexo.show();
        return undefined;
      })
      .catch(console.error);
  }, []);

  return (
    <View style={styles.container}>
      <Button title="Send feedback" onPress={() => AntNexo.show().catch(console.error)} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
