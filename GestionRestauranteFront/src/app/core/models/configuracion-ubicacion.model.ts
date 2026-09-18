export interface ConfiguracionUbicacion {
  lat: number;
  lng: number;
  radioMetros: number;
  updatedAt: string;
}

export type ConfiguracionUbicacionRequest = Pick<ConfiguracionUbicacion, 'lat' | 'lng' | 'radioMetros'>;
