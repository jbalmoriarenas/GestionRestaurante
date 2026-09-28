export interface SolicitarRecuperacionRequest {
  email: string;
}

export interface RestablecerPasswordRequest {
  token: string;
  password: string;
}
