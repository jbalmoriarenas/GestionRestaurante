import { Permiso } from './permiso.model';
import { TipoUsuario } from './empleado.model';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  empleadoId: number;
  nombre: string;
  apellidos: string;
  tipoUsuario: TipoUsuario;
  permisos: Permiso[];
}
