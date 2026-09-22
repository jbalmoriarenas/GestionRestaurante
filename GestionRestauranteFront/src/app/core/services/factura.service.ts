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
  numeroFactura: string | null;
  producto: string;
  fechaFactura: string;
  fechaVencimiento: string | null;
  importeBase: number | null;
  iva: number | null;
  importeTotal: number;
  estadoPago: EstadoPago;
  archivo: File;
  // Campos detectados por OCR que no encajan en ningun campo fijo (fase2-3): se envian como
  // un JSON serializado en un campo de texto del propio multipart/form-data (no hay forma
  // nativa de mandar un mapa arbitrario en FormData) - el backend (FacturaCreateDTO) lo
  // deserializa con Jackson desde el campo "camposAdicionalesJson".
  camposAdicionales?: Record<string, string>;
  // true cuando el alta parte de una previsualizacion de /extraer (aunque el Admin haya
  // editado los valores propuestos) - determina estadoExtraccion = REVISADA vs MANUAL.
  origenOcr?: boolean;
  // JSON crudo de la previsualizacion de /extraer (FacturaExtraccionPreview completa), para
  // auditoria (facturas.datos_extraidos_raw) - solo tiene sentido cuando origenOcr es true.
  datosExtraidosRawJson?: string;
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
    formData.set('producto', payload.producto);
    formData.set('fechaFactura', payload.fechaFactura);
    if (payload.fechaVencimiento !== null) {
      formData.set('fechaVencimiento', payload.fechaVencimiento);
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
    if (payload.camposAdicionales && Object.keys(payload.camposAdicionales).length > 0) {
      formData.set('camposAdicionalesJson', JSON.stringify(payload.camposAdicionales));
    }
    if (payload.origenOcr) {
      formData.set('origenOcr', 'true');
      if (payload.datosExtraidosRawJson) {
        formData.set('datosExtraidosRawJson', payload.datosExtraidosRawJson);
      }
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
  ): Observable<Blob> {
    const params = buildFiltroParams(tipo, proveedorId, estadoPago, mes, anio);
    return this.http.get(`${BASE_URL}/exportar`, { params, responseType: 'blob' });
  }

  extraer(archivo: File, tipo: TipoProveedor): Observable<FacturaExtraccionPreview> {
    const formData = new FormData();
    formData.set('archivo', archivo);
    formData.set('tipo', tipo);
    return this.http.post<FacturaExtraccionPreview>(`${BASE_URL}/extraer`, formData);
  }
}
