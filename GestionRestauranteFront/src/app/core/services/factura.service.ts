import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { EstadoPago, FacturaDetalle, FacturaExtraccionPreview, FacturaListado } from '../models/factura.model';
import { Page } from '../models/page.model';
import { TipoProveedor } from '../models/proveedor.model';

const BASE_URL = `${API_BASE_URL}/facturas`;

export interface FacturaCreatePayload {
  proveedorId: number;
  // El formulario de alta ya no pide "numero de factura" (sin sentido para facturas con
  // muchas lineas de articulos distintos, ver planesImplementacion/bbdd/fase2-3-bbdd.md) - null.
  numeroFactura: string | null;
  // Unica fecha obligatoria (de ella se derivan mes/anio) y un metadato opcional adicional que
  // puede traer un ticket de venta al por mayor.
  fechaImpresion: string;
  fechaEntrega: string | null;
  importeBase: number | null;
  iva: number | null;
  importeTotal: number;
  estadoPago: EstadoPago;
  archivo: File;
  // true cuando el alta parte de una previsualizacion de /extraer (aunque el Admin haya
  // editado los valores propuestos) - determina estadoExtraccion = REVISADA vs MANUAL.
  origenOcr?: boolean;
}

function buildFiltroParams(
  tipo: TipoProveedor,
  proveedorId: number | null,
  estadoPago: EstadoPago | null,
  mes: number | null,
  anio: number | null,
): HttpParams {
  let params = new HttpParams().set('tipo', tipo);
  if (proveedorId !== null) {
    params = params.set('proveedorId', proveedorId);
  }
  if (estadoPago !== null) {
    params = params.set('estadoPago', estadoPago);
  }
  if (mes !== null) {
    params = params.set('mes', mes);
  }
  if (anio !== null) {
    params = params.set('anio', anio);
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class FacturaService {
  constructor(private readonly http: HttpClient) {}

  listar(
    tipo: TipoProveedor,
    proveedorId: number | null,
    estadoPago: EstadoPago | null,
    mes: number | null,
    anio: number | null,
    page: number,
    size: number,
  ): Observable<Page<FacturaListado>> {
    const params = buildFiltroParams(tipo, proveedorId, estadoPago, mes, anio).set('page', page).set('size', size);
    return this.http.get<Page<FacturaListado>>(BASE_URL, { params });
  }

  obtenerDetalle(id: number): Observable<FacturaDetalle> {
    return this.http.get<FacturaDetalle>(`${BASE_URL}/${id}`);
  }

  crear(payload: FacturaCreatePayload): Observable<FacturaDetalle> {
    const formData = new FormData();
    formData.set('proveedorId', String(payload.proveedorId));
    if (payload.numeroFactura !== null) {
      formData.set('numeroFactura', payload.numeroFactura);
    }
    formData.set('fechaImpresion', payload.fechaImpresion);
    if (payload.fechaEntrega !== null) {
      formData.set('fechaEntrega', payload.fechaEntrega);
    }
    if (payload.importeBase !== null) {
      formData.set('importeBase', String(payload.importeBase));
    }
    if (payload.iva !== null) {
      formData.set('iva', String(payload.iva));
    }
    formData.set('importeTotal', String(payload.importeTotal));
    formData.set('estadoPago', payload.estadoPago);
    formData.set('archivo', payload.archivo);
    if (payload.origenOcr) {
      formData.set('origenOcr', 'true');
    }
    return this.http.post<FacturaDetalle>(BASE_URL, formData);
  }

  cambiarEstadoPago(id: number, estadoPago: EstadoPago): Observable<FacturaDetalle> {
    return this.http.patch<FacturaDetalle>(`${BASE_URL}/${id}/estado-pago`, { estadoPago });
  }

  descargarArchivo(id: number): Observable<Blob> {
    return this.http.get(`${BASE_URL}/${id}/archivo`, { responseType: 'blob' });
  }

  exportar(
    tipo: TipoProveedor,
    proveedorId: number | null,
    estadoPago: EstadoPago | null,
    mes: number | null,
    anio: number | null,
    titulo: string,
    cabeceras: string[],
  ): Observable<Blob> {
    // El back no tiene ningun mecanismo de i18n para el resto de la app: el titulo y las
    // cabeceras de columna del Excel se traducen aqui (idioma activo) y se mandan ya resueltos.
    let params = buildFiltroParams(tipo, proveedorId, estadoPago, mes, anio).set('titulo', titulo);
    for (const cabecera of cabeceras) {
      params = params.append('cabecera', cabecera);
    }
    return this.http.get(`${BASE_URL}/exportar`, { params, responseType: 'blob' });
  }

  exportarZip(
    tipo: TipoProveedor,
    proveedorId: number | null,
    estadoPago: EstadoPago | null,
    mes: number,
    anio: number,
    nombreCarpeta: string,
  ): Observable<Blob> {
    const params = buildFiltroParams(tipo, proveedorId, estadoPago, mes, anio).set('nombreCarpeta', nombreCarpeta);
    return this.http.get(`${BASE_URL}/exportar-zip`, { params, responseType: 'blob' });
  }

  extraer(archivo: File, tipo: TipoProveedor): Observable<FacturaExtraccionPreview> {
    const formData = new FormData();
    formData.set('archivo', archivo);
    formData.set('tipo', tipo);
    return this.http.post<FacturaExtraccionPreview>(`${BASE_URL}/extraer`, formData);
  }
}
