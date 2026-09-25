export interface ConexionEstado {
  conectado: boolean;
  nombreUbicacion: string | null;
  fechaConexion: string | null;
  fechaUltimaSincronizacion: string | null;
  ultimoError: string | null;
}

export interface ResultadoSincronizacion {
  nuevas: number;
  actualizadas: number;
  total: number;
  // null si la sincronizacion se completo sin problemas; si no es null, nuevas/actualizadas/total
  // son siempre 0 y hay que mostrar el fallo en vez de un mensaje de exito.
  error: string | null;
}

export interface Valoracion {
  id: number;
  autorNombre: string;
  autorFotoUrl: string | null;
  puntuacion: number; // 1-5
  comentario: string | null;
  fechaPublicacion: string;
  fechaActualizacion: string | null;
}

export interface ResumenValoraciones {
  media: number | null;
  total: number;
  distribucion: Record<number, number>; // claves 1..5
}
