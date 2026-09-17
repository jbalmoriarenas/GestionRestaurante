import { CodigoVista } from '../core/models/permiso.model';
import { IconName } from '../shared/ui/ui-icon/ui-icon';

export interface MenuItem {
  vista: CodigoVista;
  ruta: string;
  /** Clave de traducción: 'menu.' + labelKey */
  labelKey: string;
  icono: IconName;
}

export const MENU_ITEMS: MenuItem[] = [
  { vista: 'FICHAJES', ruta: '/fichajes', labelKey: 'fichajes', icono: 'fichajes' },
  { vista: 'EMPLEADOS', ruta: '/empleados', labelKey: 'empleados', icono: 'empleados' },
  { vista: 'FACTURAS', ruta: '/facturas', labelKey: 'facturas', icono: 'facturas' },
  { vista: 'NOMINAS', ruta: '/nominas', labelKey: 'nominas', icono: 'nominas' },
];
