export type TipoProveedor = 'PROVEEDOR' | 'SUMINISTRO';

export interface Proveedor {
  id: number;
  nombre: string;
  cif: string | null;
  emailContacto: string | null;
  telefono: string | null;
  tipo: TipoProveedor;
}

export interface ProveedorCreate {
  nombre: string;
  cif: string | null;
  emailContacto: string | null;
  telefono: string | null;
  tipo: TipoProveedor;
}
