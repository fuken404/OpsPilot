import { describe, expect, it } from 'vitest';
import { formatMinutes } from './models';

describe('formatMinutes', () => {
  it('muestra minutos, horas y días', () => {
    expect(formatMinutes(0)).toBe('0 min');
    expect(formatMinutes(45)).toBe('45 min');
    expect(formatMinutes(135)).toBe('2 h 15 min');
    expect(formatMinutes(-90)).toBe('1 h 30 min');
    expect(formatMinutes(3 * 1440 + 125)).toBe('3 d 2 h');
    expect(formatMinutes(1440, false)).toBe('24 h');
  });
});
