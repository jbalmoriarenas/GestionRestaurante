/**
 * "Hoy" en formato yyyy-MM-dd, en la zona horaria LOCAL del navegador. Deliberadamente no usa
 * Date#toISOString() (siempre UTC): en una zona por delante de UTC (p.ej. España en CEST,
 * UTC+2), entre medianoche y las ~2h locales toISOString() todavia devuelve el dia anterior,
 * lo que desalinearia cualquier limite de "no fechas pasadas" que se calcule con esta funcion.
 */
export function hoyLocalISO(): string {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}
