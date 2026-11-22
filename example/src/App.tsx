import { useEffect } from 'react';
import { Button, StyleSheet, View } from 'react-native';
import { AntNexo } from '@antnexo/react-native';

export default function App() {
  useEffect(() => {
    AntNexo.configure({
      host: 'https://splynx.antnexo.com',
      workspace: 'splynx',
    }).catch(console.error);
  }, []);

  return (
    <View style={styles.container}>
      <Button
        title="Send feedback"
        onPress={() => AntNexo.show().catch(console.error)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
