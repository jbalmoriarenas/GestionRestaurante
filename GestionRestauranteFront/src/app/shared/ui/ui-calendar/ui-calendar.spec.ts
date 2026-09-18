import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UiCalendar } from './ui-calendar';

describe('UiCalendar', () => {
  let fixture: ComponentFixture<UiCalendar>;
  let componente: UiCalendar;

  function configurar(anio: number, mes: number): void {
    fixture = TestBed.createComponent(UiCalendar);
    componente = fixture.componentInstance;
    fixture.componentRef.setInput('anio', anio);
    fixture.componentRef.setInput('mes', mes);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [UiCalendar] });
  });

  it('enero 2026 empieza en jueves: los primeros 3 huecos son de diciembre (fuera de mes)', () => {
    configurar(2026, 1);

    const dias = componente.dias();
    expect(dias[0].fueraDeMes).toBe(true);
    expect(dias[0].fecha.getMonth()).toBe(11); // diciembre
    expect(dias[3].fueraDeMes).toBe(false);
    expect(dias[3].numero).toBe(1);
  });

  it('marca un dia como festivo cuando su fecha coincide con un marcador', () => {
    configurar(2026, 1);
    fixture.componentRef.setInput('marcadores', [{ fecha: '2026-01-01', variante: 'festivo' }]);
    fixture.detectChanges();

    const diaUno = componente.dias().find((dia) => dia.numero === 1 && !dia.fueraDeMes);
    expect(diaUno?.variante).toBe('festivo');
  });

  it('marca un dia como aprobado cuando su fecha coincide con un marcador de ese tipo', () => {
    configurar(2026, 1);
    fixture.componentRef.setInput('marcadores', [{ fecha: '2026-01-05', variante: 'aprobado' }]);
    fixture.detectChanges();

    const dia = componente.dias().find((d) => d.numero === 5 && !d.fueraDeMes);
    expect(dia?.variante).toBe('aprobado');
  });

  it('marca un dia como pendiente cuando su fecha coincide con un marcador de ese tipo', () => {
    configurar(2026, 1);
    fixture.componentRef.setInput('marcadores', [{ fecha: '2026-01-06', variante: 'pendiente' }]);
    fixture.detectChanges();

    const dia = componente.dias().find((d) => d.numero === 6 && !d.fueraDeMes);
    expect(dia?.variante).toBe('pendiente');
  });

  it('un mes de 31 dias que empieza en sabado genera 6 semanas completas (42 celdas)', () => {
    // agosto 2026 empieza en sabado
    configurar(2026, 8);

    const dias = componente.dias();
    expect(dias.length).toBe(42);
    expect(dias[0].fueraDeMes).toBe(true); // dias de julio rellenando la primera semana
    expect(dias.at(-1)?.fueraDeMes).toBe(true); // dias de septiembre rellenando la ultima
  });

  it('emite mesAnterior/mesSiguiente al pulsar los botones de navegacion', () => {
    configurar(2026, 1);
    let anteriorEmitido = false;
    let siguienteEmitido = false;
    componente.mesAnterior.subscribe(() => (anteriorEmitido = true));
    componente.mesSiguiente.subscribe(() => (siguienteEmitido = true));

    componente.irAMesAnterior();
    componente.irAMesSiguiente();

    expect(anteriorEmitido).toBe(true);
    expect(siguienteEmitido).toBe(true);
  });
});
