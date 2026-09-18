import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { LayoutService } from '../../core/services/layout.service';
import { Sidebar } from './sidebar';

describe('Sidebar', () => {
  let routerEvents: Subject<unknown>;
  let routerMock: { events: Subject<unknown>; url: string };
  let fixtureActual: ComponentFixture<Sidebar> | null = null;

  function crearComponente(): Sidebar {
    const fixture = TestBed.createComponent(Sidebar);
    fixture.detectChanges();
    fixtureActual = fixture;
    return fixture.componentInstance;
  }

  // El efecto que resetea el cierre manual (sidebar.ts) se agenda de forma asincrona, no
  // se ejecuta en el mismo tick que la señal que lo dispara. detectChanges() fuerza a
  // Angular a vaciar los efectos pendientes antes de leer openGroups().
  function navegarA(url: string): void {
    routerMock.url = url;
    routerEvents.next(new NavigationEnd(0, url, url));
    fixtureActual?.detectChanges();
  }

  beforeEach(() => {
    routerEvents = new Subject();
    routerMock = { events: routerEvents, url: '/fichajes' };

    TestBed.configureTestingModule({
      imports: [Sidebar],
      providers: [
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: {} },
        { provide: AuthService, useValue: { tieneAcceso: () => true, puedeEditar: () => true } },
        { provide: LayoutService, useValue: { mobileMenuOpen: () => false, closeMobileMenu: () => {} } },
        provideTranslateService(),
      ],
    });
  });

  it('el grupo "fichajes" empieza abierto porque la ruta activa es una de sus hijas', () => {
    const componente = crearComponente();

    expect(componente.openGroups()['fichajes']).toBe(true);
  });

  it('cerrar manualmente el grupo lo colapsa aunque la ruta activa siga siendo suya', () => {
    const componente = crearComponente();

    componente.toggleGroup('fichajes');

    expect(componente.openGroups()['fichajes']).toBe(false);
  });

  it('al navegar, se olvida el cierre manual y el grupo vuelve a abrirse si la nueva ruta es suya', () => {
    const componente = crearComponente();
    componente.toggleGroup('fichajes');
    expect(componente.openGroups()['fichajes']).toBe(false);

    // La señal de la URL activa solo notifica si el valor cambia de verdad, así que hay
    // que navegar a otra ruta antes de volver — igual que en el repro real (cerrar en
    // /fichajes, ir a /empleados, volver a /fichajes).
    navegarA('/empleados');
    navegarA('/fichajes');

    expect(componente.openGroups()['fichajes']).toBe(true);
  });

  it('al navegar fuera del grupo, este no se abre solo porque antes estuviera abierto', () => {
    const componente = crearComponente();
    expect(componente.openGroups()['fichajes']).toBe(true);

    navegarA('/empleados');

    expect(componente.openGroups()['fichajes']).toBe(false);
  });
});
