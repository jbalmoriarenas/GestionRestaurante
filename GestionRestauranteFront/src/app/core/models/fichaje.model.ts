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

export interface FichajeResumenDia {
  fecha: string; // yyyy-MM-dd
  horasCalculadas: number;
  horasTeoricas: number;
  cumpleMinimo: boolean;
}

export interface FichajeResumenSemana {
  inicioSemana: string; // yyyy-MM-dd
  finSemana: string; // yyyy-MM-dd
  horasCalculadas: number;
  horasTeoricas: number;
  cumpleMinimo: boolean;
}

export interface FichajeResumenMes {
  mes: number; // 1-12
  anio: number;
  horasCalculadas: number;
  horasTeoricas: number;
  cumpleMinimo: boolean;
}

export interface FichajeCorreccionRequest {
  horaEntrada: string; // ISO 8601
  horaSalida: string; // ISO 8601
  observaciones: string | null;
}
