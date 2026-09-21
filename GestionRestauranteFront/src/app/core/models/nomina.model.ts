export type EstadoNomina = 'PENDIENTE' | 'ADJUNTADA' | 'ENVIADA';

export interface NominaListado {
  empleadoId: number;
  nombreEmpleado: string;
  email: string;
  periodoMes: number;
  periodoAnio: number;
  estado: EstadoNomina;
  nominaId: number | null;
  nombreArchivoOriginal: string | null;
  fechaSubida: string | null;
  fechaEnvio: string | null;
}

// Motivo por el que una entrada del ZIP no se pudo asignar automáticamente a ningún
// empleado (ver NominaService.procesarZip en el backend, fase3-2-back.md).
export type MotivoPendienteNomina = 'NO_ES_PDF' | 'NOMBRE_NO_VALIDO' | 'EMPLEADO_NO_ENCONTRADO';

export interface NominaPendiente {
  tempId: string;
  nombreArchivoOriginal: string;
  motivo: MotivoPendienteNomina;
}

export interface ProcesamientoZip {
  asignadosAutomaticamente: number;
  pendientes: NominaPendiente[];
}

// Forma exacta de la respuesta del backend al subir/asignar/enviar una nómina individual
// (NominaResponseDTO): no incluye nombreEmpleado/email (el backend no los necesita
// recalcular en esa respuesta puntual) — el componente combina esto con la fila existente
// de NominaListado en vez de sustituirla entera, para no perder esos campos.
export interface NominaAccionResponse {
  id: number;
  empleadoId: number;
  periodoMes: number;
  periodoAnio: number;
  estado: EstadoNomina;
  nombreArchivoOriginal: string;
  fechaSubida: string;
  fechaEnvio: string | null;
}
