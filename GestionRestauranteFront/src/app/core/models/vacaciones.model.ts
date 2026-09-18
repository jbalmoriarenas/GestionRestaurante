export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';
export type TipoDiaCalendario = 'FESTIVO' | 'APROBADO' | 'PENDIENTE';

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
