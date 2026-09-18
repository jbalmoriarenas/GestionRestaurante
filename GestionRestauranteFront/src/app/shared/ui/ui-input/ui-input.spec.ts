import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { UiInput } from './ui-input';

describe('UiInput', () => {
  let fixture: ComponentFixture<UiInput>;
  let componente: UiInput;

  function nativeInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input');
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [UiInput] });
    fixture = TestBed.createComponent(UiInput);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('writeValue() actualiza el valor mostrado en el input nativo', () => {
    componente.writeValue('hola');
    fixture.detectChanges();

    expect(nativeInput().value).toBe('hola');
  });

  it('handleInput() propaga el valor via registerOnChange', () => {
    const onChange = vi.fn();
    componente.registerOnChange(onChange);
    const input = nativeInput();
    input.value = 'nuevo valor';

    componente.handleInput({ target: input } as unknown as Event);

    expect(onChange).toHaveBeenCalledWith('nuevo valor');
    expect(componente.value()).toBe('nuevo valor');
  });

  it('setDisabledState() deshabilita el input nativo', () => {
    componente.setDisabledState(true);
    fixture.detectChanges();

    expect(nativeInput().disabled).toBe(true);
  });

  it('sin min/max, el input nativo no lleva esos atributos', () => {
    expect(nativeInput().getAttribute('min')).toBeNull();
    expect(nativeInput().getAttribute('max')).toBeNull();
  });

  it('con min/max establecidos, se reflejan como atributos del input nativo', () => {
    fixture.componentRef.setInput('min', '2026-01-01');
    fixture.componentRef.setInput('max', '2026-12-31');
    fixture.detectChanges();

    expect(nativeInput().getAttribute('min')).toBe('2026-01-01');
    expect(nativeInput().getAttribute('max')).toBe('2026-12-31');
  });
});
