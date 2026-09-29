import { tieneAccesoItem } from './menu-items';

describe('tieneAccesoItem', () => {
  function mockAuthService(opciones: { tieneAcceso?: boolean; puedeEditar?: boolean }) {
    return {
      tieneAcceso: () => opciones.tieneAcceso ?? false,
      puedeEditar: () => opciones.puedeEditar ?? false,
    };
  }

  it('sin nivelMinimo, se basa en tieneAcceso (VISUALIZACION o superior)', () => {
    const authService = mockAuthService({ tieneAcceso: true, puedeEditar: false });

    expect(tieneAccesoItem(authService, { vista: 'FACTURAS' })).toBe(true);
  });

  it('sin nivelMinimo, deniega si tieneAcceso es false aunque puedeEditar sea true', () => {
    const authService = mockAuthService({ tieneAcceso: false, puedeEditar: true });

    expect(tieneAccesoItem(authService, { vista: 'FACTURAS' })).toBe(false);
  });

  it('con nivelMinimo EDICION, se basa en puedeEditar', () => {
    const authService = mockAuthService({ tieneAcceso: true, puedeEditar: false });

    expect(tieneAccesoItem(authService, { vista: 'FACTURAS', nivelMinimo: 'EDICION' })).toBe(false);
  });

  it('con nivelMinimo EDICION, concede si puedeEditar es true', () => {
    const authService = mockAuthService({ tieneAcceso: true, puedeEditar: true });

    expect(tieneAccesoItem(authService, { vista: 'FACTURAS', nivelMinimo: 'EDICION' })).toBe(true);
  });

  it('con nivelMinimo VISUALIZACION explicito, se basa en tieneAcceso (igual que sin nivelMinimo)', () => {
    const authService = mockAuthService({ tieneAcceso: true, puedeEditar: false });

    expect(tieneAccesoItem(authService, { vista: 'FACTURAS', nivelMinimo: 'VISUALIZACION' })).toBe(true);
  });
});
