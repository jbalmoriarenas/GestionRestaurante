export type NivelAcceso = 'EDICION' | 'VISUALIZACION';

export type CodigoVista = 'EMPLEADOS' | 'FICHAJES' | 'FACTURAS' | 'NOMINAS';

export interface Permiso {
  vista: CodigoVista;
  nivelAcceso: NivelAcceso;
}
