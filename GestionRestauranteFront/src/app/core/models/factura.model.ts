import { TipoProveedor } from './proveedor.model';

export type EstadoPago = 'PENDIENTE' | 'PAGADA';

// Estado del pipeline de OCR (fase2-3), independiente de EstadoPago (negocio: si ya se ha
// pagado). MANUAL = factura dada de alta sin pasar por extraccion automatica.
export type EstadoExtraccion = 'MANUAL' | 'PENDIENTE' | 'PROCESADA' | 'ERROR' | 'REVISADA';

// Columnas exactas pedidas por el usuario para el listado: Empresa/Fecha de impresion/Fecha de
// entrega/Base imponible/IVA/Cuantia/Estado. El resto de campos de la factura solo se exponen
// en FacturaDetalle y en la exportacion a Excel.
export interface FacturaListado {
  id: number;
  empresa: string;
  // Unica fecha obligatoria (sustituye a la antigua "fecha de venta"): de ella se derivan
  // mes/anio en el servidor.
  fechaImpresion: string;
  fechaEntrega: string | null;
  importeBase: number | null;
  iva: number | null;
  importeTotal: number;
  estadoPago: EstadoPago;
}

export interface FacturaDetalle extends FacturaListado {
  proveedorId: number;
  tipo: TipoProveedor;
  numeroFactura: string | null;
  nombreArchivoOriginal: string;
  fechaSubida: string;
  estadoExtraccion: EstadoExtraccion;
}

// Respuesta de POST /api/facturas/extraer (fase2-3): nunca persiste nada, solo propone
// valores para precargar el formulario de alta - el Admin revisa/corrige antes de confirmar.
// Solo cubre los campos fijos de Factura (fechas, importes); cualquier otro dato de la factura
// (incluido el proveedor) esta siempre a entrada manual o en el PDF/imagen original, descargable
// desde el detalle.
export interface FacturaExtraccionPreview {
  fechaImpresionSugerida: string | null;
  fechaEntregaSugerida: string | null;
  importeBaseSugerido: number | null;
  ivaSugerido: number | null;
  importeTotalSugerido: number | null;
}
