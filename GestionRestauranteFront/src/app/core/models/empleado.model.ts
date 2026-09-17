export interface TipoUsuario {
  codigo: string;
  nombre: string;
}

export interface Empleado {
  id: number;
  nombre: string;
  apellidos: string;
  email: string;
  dniNie: string;
  telefono: string | null;
  puesto: string | null;
  fechaAlta: string;
  fechaBaja: string | null;
  activo: boolean;
  tipoUsuario: TipoUsuario;
}

export interface EmpleadoCreateRequest {
  nombre: string;
  apellidos: string;
  email: string;
  dniNie: string;
  telefono: string | null;
  puesto: string | null;
  fechaAlta: string;
  tipoUsuarioCodigo: string;
  password: string;
}

export type EmpleadoUpdateRequest = Omit<EmpleadoCreateRequest, 'password'>;
