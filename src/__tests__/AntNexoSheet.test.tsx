import { StrictMode } from 'react';
import { render, act } from '@testing-library/react-native';

const mockEnds: Array<() => void> = [];
const mockAntNexo = {
  show: jest.fn(() => new Promise<void>((resolve) => mockEnds.push(resolve))),
  close: jest.fn(),
};
jest.mock('../AntNexo', () => ({ AntNexo: mockAntNexo }));

const { AntNexoSheet } =
  require('../AntNexoSheet') as typeof import('../AntNexoSheet');

beforeEach(() => {
  mockEnds.length = 0;
  mockAntNexo.show.mockClear();
  mockAntNexo.close.mockClear();
});

test('visible shows the sheet; when it is gone, onDismiss once', async () => {
  const onDismiss = jest.fn();
  const r = await render(
    <AntNexoSheet
      visible={false}
      onDismiss={onDismiss}
      tab="roadmap"
      screenshot
    />
  );
  expect(mockAntNexo.show).not.toHaveBeenCalled();
  await r.rerender(
    <AntNexoSheet visible onDismiss={onDismiss} tab="roadmap" screenshot />
  );
  expect(mockAntNexo.show).toHaveBeenCalledWith({
    tab: 'roadmap',
    screenshot: true,
  });
  await act(async () => mockEnds[0]!());
  expect(onDismiss).toHaveBeenCalledTimes(1);
  expect(mockAntNexo.close).not.toHaveBeenCalled();
});

test('visible turned off while open closes it', async () => {
  const onDismiss = jest.fn();
  const r = await render(<AntNexoSheet visible onDismiss={onDismiss} />);
  await r.rerender(<AntNexoSheet visible={false} onDismiss={onDismiss} />);
  await act(async () => new Promise<void>((resolve) => setTimeout(resolve, 0)));
  expect(mockAntNexo.close).toHaveBeenCalledTimes(1);
  await act(async () => mockEnds[0]!());
  expect(onDismiss).toHaveBeenCalledTimes(1);
});

test('unmounting while open closes it, and no onDismiss after unmount', async () => {
  const onDismiss = jest.fn();
  const r = await render(<AntNexoSheet visible onDismiss={onDismiss} />);
  await r.unmount();
  await act(async () => new Promise<void>((resolve) => setTimeout(resolve, 0)));
  expect(mockAntNexo.close).toHaveBeenCalledTimes(1);
  await act(async () => mockEnds[0]!());
  expect(onDismiss).not.toHaveBeenCalled();
});

test('a show that fails still dismisses', async () => {
  const err = jest.spyOn(console, 'error').mockImplementation(() => {});
  mockAntNexo.show.mockImplementationOnce(() =>
    Promise.reject(new Error('not_configured'))
  );
  const onDismiss = jest.fn();
  await render(<AntNexoSheet visible onDismiss={onDismiss} />);
  await act(async () => {});
  expect(onDismiss).toHaveBeenCalledTimes(1);
  err.mockRestore();
});

test('the latest onDismiss is called', async () => {
  const first = jest.fn();
  const second = jest.fn();
  const r = await render(<AntNexoSheet visible onDismiss={first} />);
  await r.rerender(<AntNexoSheet visible onDismiss={second} />);
  expect(mockAntNexo.show).toHaveBeenCalledTimes(1);
  await act(async () => mockEnds[0]!());
  expect(first).not.toHaveBeenCalled();
  expect(second).toHaveBeenCalledTimes(1);
});

test('StrictMode (mount, cleanup, mount): one opening, not closed, one onDismiss', async () => {
  const onDismiss = jest.fn();
  await render(
    <StrictMode>
      <AntNexoSheet visible onDismiss={onDismiss} />
    </StrictMode>
  );
  await act(async () => new Promise<void>((resolve) => setTimeout(resolve, 0)));
  expect(mockAntNexo.show).toHaveBeenCalledTimes(1);
  expect(mockAntNexo.close).not.toHaveBeenCalled();
  expect(onDismiss).not.toHaveBeenCalled();
  await act(async () => mockEnds[0]!());
  expect(onDismiss).toHaveBeenCalledTimes(1);
});

test('visible true, false, true at once keeps the one opening', async () => {
  const onDismiss = jest.fn();
  const r = await render(<AntNexoSheet visible onDismiss={onDismiss} />);
  await r.rerender(<AntNexoSheet visible={false} onDismiss={onDismiss} />);
  await r.rerender(<AntNexoSheet visible onDismiss={onDismiss} />);
  await act(async () => new Promise<void>((resolve) => setTimeout(resolve, 0)));
  expect(mockAntNexo.show).toHaveBeenCalledTimes(1);
  expect(mockAntNexo.close).not.toHaveBeenCalled();
  expect(onDismiss).not.toHaveBeenCalled();
});
