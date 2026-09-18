import { CodigoVista, NivelAcceso } from '../core/models/permiso.model';
import { IconName } from '../shared/ui/ui-icon/ui-icon';

export interface MenuChild {
  ruta: string;
  /** Clave de traducción: 'menu.' + labelKey */
  labelKey: string;
  vista: CodigoVista;
  nivelMinimo?: NivelAcceso;
}

export interface MenuLeaf {
  labelKey: string;
  icono: IconName;
  ruta: string;
  vista: CodigoVista;
  nivelMinimo?: NivelAcceso;
  children?: undefined;
}

export interface MenuGroup {
  labelKey: string;
  icono: IconName;
  children: MenuChild[];
  ruta?: undefined;
}

export type MenuItem = MenuLeaf | MenuGroup;

export const MENU_ITEMS: MenuItem[] = [
  {
    labelKey: 'fichajes',
    icono: 'fichajes',
    children: [{ ruta: '/fichajes', labelKey: 'fichajesMio', vista: 'FICHAJES', nivelMinimo: 'EDICION' }],
  },
  {
    labelKey: 'vacaciones',
    icono: 'vacaciones',
    children: [{ ruta: '/vacaciones', labelKey: 'vacacionesMias', vista: 'VACACIONES', nivelMinimo: 'EDICION' }],
  },
  { vista: 'EMPLEADOS', ruta: '/empleados', labelKey: 'empleados', icono: 'empleados' },
  { vista: 'FACTURAS', ruta: '/facturas', labelKey: 'facturas', icono: 'facturas' },
  { vista: 'NOMINAS', ruta: '/nominas', labelKey: 'nominas', icono: 'nominas' },
];
