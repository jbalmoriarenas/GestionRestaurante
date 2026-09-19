export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';
export type TipoDiaCalendario = 'FESTIVO' | 'APROBADO' | 'PENDIENTE';

// Opciones cerradas para el motivo de rechazo (ver vacaciones.admin.rechazo.motivos /
// vacaciones.motivosRechazo en los i18n): deben coincidir exactamente con el @Pattern de
// RechazarSolicitudRequestDTO en el backend.
export const MOTIVOS_RECHAZO_VACACIONES = ['FALTA_DISPONIBILIDAD', 'YA_SELECCIONADAS_COMPANEROS', 'OTROS'] as const;
export type MotivoRechazoVacaciones = (typeof MOTIVOS_RECHAZO_VACACIONES)[number];

export interface ResumenVacaciones {
  total: number;
  aprobados: number;
  pendientes: number;
  disponibles: number;
}

export interface DiaCalendario {
  fecha: string; // yyyy-MM-dd
  tipo: TipoDiaCalendario;
}

export interface SolicitudVacaciones {
  id: number;
  fechaInicio: string;
  fechaFin: string;
  diasSolicitados: number;
  estado: EstadoSolicitud;
  fechaSolicitud: string;
  motivoRechazo: string | null;
}

export type SolicitudVacacionesRequest = Pick<SolicitudVacaciones, 'fechaInicio' | 'fechaFin'>;

export interface SolicitudVacacionesAdmin {
  id: number;
  empleadoId: number;
  empleadoNombre: string;
  fechaInicio: string;
  fechaFin: string;
  diasSolicitados: number;
  estado: EstadoSolicitud;
  fechaSolicitud: string;
  motivoRechazo: string | null;
}

export interface AsignacionOficioRequest {
  empleadoId: number;
  fechaInicio: string;
  fechaFin: string;
}
