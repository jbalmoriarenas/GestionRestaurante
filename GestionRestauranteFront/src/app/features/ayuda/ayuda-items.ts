import { CodigoVista, NivelAcceso } from '../../core/models/permiso.model';

export interface AyudaItem {
  ruta: string;
  vista: CodigoVista;
  nivelMinimo?: NivelAcceso;
  /** Clave i18n reutilizada de layout/menu-items.ts / app.routes.ts (data.title) */
  tituloKey: string;
  /** Clave i18n nueva, bajo 'ayuda.items.<clave>.descripcion' */
  descripcionKey: string;
  /** Vacío hasta que exista el vídeo real de esa vista */
  videoUrl: string;
}

export const AYUDA_ITEMS: AyudaItem[] = [
  {
    ruta: '/fichajes',
    vista: 'FICHAJES',
    nivelMinimo: 'EDICION',
    tituloKey: 'menu.fichajesMio',
    descripcionKey: 'ayuda.items.fichajesMio.descripcion',
    videoUrl: '', // TODO vídeo: Mi fichaje
  },
  {
    ruta: '/fichajes/admin',
    vista: 'EMPLEADOS',
    nivelMinimo: 'EDICION',
    tituloKey: 'fichajes.admin.titulo',
    descripcionKey: 'ayuda.items.fichajesAdmin.descripcion',
    videoUrl: '', // TODO vídeo: Fichajes (Admin)
  },
  {
    ruta: '/vacaciones',
    vista: 'VACACIONES',
    nivelMinimo: 'EDICION',
    tituloKey: 'menu.vacacionesMias',
    descripcionKey: 'ayuda.items.vacacionesMias.descripcion',
    videoUrl: '', // TODO vídeo: Mis vacaciones
  },
  {
    ruta: '/vacaciones/admin',
    vista: 'EMPLEADOS',
    nivelMinimo: 'EDICION',
    tituloKey: 'vacaciones.admin.titulo',
    descripcionKey: 'ayuda.items.vacacionesAdmin.descripcion',
    videoUrl: '', // TODO vídeo: Vacaciones (Admin)
  },
  {
    ruta: '/empleados',
    vista: 'EMPLEADOS',
    tituloKey: 'empleados.title',
    descripcionKey: 'ayuda.items.empleados.descripcion',
    videoUrl: '', // TODO vídeo: Empleados
  },
  // nivelMinimo: 'EDICION' en las tres siguientes, alineado con permissionGuard('...', 'EDICION')
  // en app.routes.ts (mismo criterio que layout/menu-items.ts) — sin esto, un futuro permiso
  // VISUALIZACION-only sobre estas vistas mostraria su tarjeta de ayuda aunque el guard de la
  // ruta siguiera bloqueando el acceso real.
  {
    ruta: '/facturas',
    vista: 'FACTURAS',
    nivelMinimo: 'EDICION',
    tituloKey: 'facturas.admin.titulo',
    descripcionKey: 'ayuda.items.facturas.descripcion',
    videoUrl: '', // TODO vídeo: Facturas
  },
  {
    ruta: '/nominas',
    vista: 'NOMINAS',
    nivelMinimo: 'EDICION',
    tituloKey: 'nominas.admin.titulo',
    descripcionKey: 'ayuda.items.nominas.descripcion',
    videoUrl: '', // TODO vídeo: Nóminas
  },
  {
    ruta: '/valoraciones',
    vista: 'VALORACIONES',
    nivelMinimo: 'EDICION',
    tituloKey: 'valoraciones.admin.titulo',
    descripcionKey: 'ayuda.items.valoraciones.descripcion',
    videoUrl: '', // TODO vídeo: Valoraciones
  },
  {
    ruta: '/configuracion',
    vista: 'EMPLEADOS',
    nivelMinimo: 'EDICION',
    tituloKey: 'configuracion.admin.titulo',
    descripcionKey: 'ayuda.items.configuracion.descripcion',
    videoUrl: '', // TODO vídeo: Configuración
  },
];
