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
    children: [
      { ruta: '/fichajes', labelKey: 'fichajesMio', vista: 'FICHAJES', nivelMinimo: 'EDICION' },
      // EMPLEADOS, no FICHAJES: alineado con el guard de la ruta y con la autorizacion del
      // backend (back/fase1-3-back.md) — "ver fichajes de cualquiera" es una capacidad de
      // gestion de empleados, no del modulo de fichaje en si.
      { ruta: '/fichajes/admin', labelKey: 'fichajesAdmin', vista: 'EMPLEADOS', nivelMinimo: 'EDICION' },
    ],
  },
  {
    labelKey: 'vacaciones',
    icono: 'vacaciones',
    children: [
      { ruta: '/vacaciones', labelKey: 'vacacionesMias', vista: 'VACACIONES', nivelMinimo: 'EDICION' },
      // EMPLEADOS, no VACACIONES: mismo razonamiento que fichajesAdmin — aprobar/asignar
      // vacaciones de cualquiera es una capacidad de gestion de empleados (fase1-4-back.md).
      { ruta: '/vacaciones/admin', labelKey: 'vacacionesAdmin', vista: 'EMPLEADOS', nivelMinimo: 'EDICION' },
    ],
  },
  { vista: 'EMPLEADOS', ruta: '/empleados', labelKey: 'empleados', icono: 'empleados' },
  { vista: 'FACTURAS', ruta: '/facturas', labelKey: 'facturas', icono: 'facturas' },
  { vista: 'NOMINAS', ruta: '/nominas', labelKey: 'nominas', icono: 'nominas' },
  { vista: 'VALORACIONES', ruta: '/valoraciones', labelKey: 'valoraciones', icono: 'valoraciones' },
];
