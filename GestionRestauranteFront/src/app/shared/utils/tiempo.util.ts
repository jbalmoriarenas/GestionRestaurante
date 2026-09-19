export function segundosATiempo(totalSegundos: number): string {
  const horas = Math.floor(totalSegundos / 3600);
  const minutos = Math.floor((totalSegundos % 3600) / 60);
  const segundos = Math.floor(totalSegundos % 60);
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${pad(horas)}:${pad(minutos)}:${pad(segundos)}`;
}

/** horasCalculadas/horasTeoricas llegan del backend como decimal (p.ej. 7.5 = 7h30m). */
export function horasATiempo(horas: number): string {
  return segundosATiempo(Math.round(horas * 3600));
}

const HORAS_DECIMAL_PATTERN = /^\d{1,2}(?:[.,]\d{1,2})?$/;
const HORAS_MINUTOS_PATTERN = /^([0-9]|1[0-9]|2[0-3]):([0-5][0-9])$/;

/**
 * Acepta exactamente dos formatos de entrada de usuario para una jornada:
 * decimal con coma o punto ("8,5") o "horas:minutos" ("8:30"). Cualquier otra
 * cosa se considera invalida (null) en vez de intentar adivinar la intencion.
 */
export function parsearHorasFlexible(valor: string): number | null {
  const texto = valor.trim();
  if (HORAS_DECIMAL_PATTERN.test(texto)) {
    return Number(texto.replace(',', '.'));
  }

  const coincidencia = HORAS_MINUTOS_PATTERN.exec(texto);
  if (coincidencia) {
    const horas = Number(coincidencia[1]);
    const minutos = Number(coincidencia[2]);
    return Math.round((horas + minutos / 60) * 100) / 100;
  }

  return null;
}
