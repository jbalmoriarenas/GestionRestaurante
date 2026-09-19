import { describe, expect, it } from 'vitest';
import { horasATiempo, segundosATiempo } from './tiempo.util';

describe('tiempo.util', () => {
  it('segundosATiempo() formatea segundos como HH:mm:ss con ceros a la izquierda', () => {
    expect(segundosATiempo(0)).toBe('00:00:00');
    expect(segundosATiempo(65)).toBe('00:01:05');
    expect(segundosATiempo(3661)).toBe('01:01:01');
  });

  it('horasATiempo() convierte horas decimales (7.5 = 7h30m) a HH:mm:ss', () => {
    expect(horasATiempo(8)).toBe('08:00:00');
    expect(horasATiempo(7.5)).toBe('07:30:00');
    expect(horasATiempo(0)).toBe('00:00:00');
  });
});
