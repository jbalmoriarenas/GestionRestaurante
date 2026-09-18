import { Injectable } from '@angular/core';

export type GeolocationErrorType = 'permisoDenegado' | 'posicionNoDisponible' | 'tiempoAgotado';

export class GeolocationError extends Error {
  constructor(readonly tipo: GeolocationErrorType) {
    super(tipo);
  }
}

export interface Coordenadas {
  lat: number;
  lng: number;
}

/**
 * Aisla navigator.geolocation del resto de la app: permite testear MiFichaje sin depender
 * de la API real del navegador, y centraliza la traduccion de sus codigos de error.
 */
@Injectable({ providedIn: 'root' })
export class GeolocationService {
  obtenerPosicionActual(): Promise<Coordenadas> {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new GeolocationError('posicionNoDisponible'));
        return;
      }

      navigator.geolocation.getCurrentPosition(
        (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
        (error) => reject(new GeolocationError(this.mapearError(error))),
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
      );
    });
  }

  private mapearError(error: GeolocationPositionError): GeolocationErrorType {
    switch (error.code) {
      case error.PERMISSION_DENIED:
        return 'permisoDenegado';
      case error.TIMEOUT:
        return 'tiempoAgotado';
      default:
        return 'posicionNoDisponible';
    }
  }
}
