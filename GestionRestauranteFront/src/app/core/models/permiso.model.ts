export type NivelAcceso = 'EDICION' | 'VISUALIZACION';

export type CodigoVista = 'EMPLEADOS' | 'FICHAJES' | 'VACACIONES' | 'FACTURAS' | 'NOMINAS';

export interface Permiso {
  vista: CodigoVista;
  nivelAcceso: NivelAcceso;
}
