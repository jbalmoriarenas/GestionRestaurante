export interface FichajeActual {
  enCurso: boolean;
  horaEntrada: string | null; // ISO 8601, solo si enCurso = true
}

export interface Fichaje {
  id: number;
  fecha: string; // yyyy-MM-dd
  horaEntrada: string; // ISO 8601
  horaSalida: string | null;
  horasCalculadas: number | null;
  observaciones: string | null;
}

export interface FichajeResumenHoy {
  fichajes: Fichaje[];
  horasImputadas: number;
  horasTeoricas: number;
}
