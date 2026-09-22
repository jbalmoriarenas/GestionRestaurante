import { TipoProveedor } from './proveedor.model';

export type EstadoPago = 'PENDIENTE' | 'PAGADA';

// Estado del pipeline de OCR (fase2-3), independiente de EstadoPago (negocio: si ya se ha
// pagado). MANUAL = factura dada de alta sin pasar por extraccion automatica.
export type EstadoExtraccion = 'MANUAL' | 'PENDIENTE' | 'PROCESADA' | 'ERROR' | 'REVISADA';

// Columnas exactas pedidas por el usuario para el listado: Empresa/Producto/Cuantia/Fecha/Estado.
// El resto de campos de la factura solo se exponen en FacturaDetalle y en la exportacion a Excel.
export interface FacturaListado {
  id: number;
  empresa: string;
  producto: string;
  importeTotal: number;
  fechaFactura: string;
  estadoPago: EstadoPago;
}

export interface CampoDinamico {
  clave: string;
  valor: string;
}

export interface FacturaDetalle extends FacturaListado {
  proveedorId: number;
  tipo: TipoProveedor;
  numeroFactura: string | null;
  fechaVencimiento: string | null;
  importeBase: number | null;
  iva: number | null;
  nombreArchivoOriginal: string;
  fechaSubida: string;
  estadoExtraccion: EstadoExtraccion;
  camposDinamicos: CampoDinamico[];
}

// Respuesta de POST /api/facturas/extraer (fase2-3): nunca persiste nada, solo propone
// valores para precargar el formulario de alta - el Admin revisa/corrige antes de confirmar.
export interface FacturaExtraccionPreview {
  proveedorSugeridoId: number | null;
  nombreProveedorSugerido: string | null;
  fechaFacturaSugerida: string | null;
  numeroFacturaSugerido: string | null;
  importeBaseSugerido: number | null;
  ivaSugerido: number | null;
  importeTotalSugerido: number | null;
  camposAdicionales: Record<string, string>;
}
