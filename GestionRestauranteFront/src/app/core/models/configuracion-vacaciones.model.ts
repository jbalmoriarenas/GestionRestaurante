export interface ConfiguracionVacaciones {
  diasAnualesDefecto: number;
  updatedAt: string;
}

export type ConfiguracionVacacionesRequest = Pick<ConfiguracionVacaciones, 'diasAnualesDefecto'>;
