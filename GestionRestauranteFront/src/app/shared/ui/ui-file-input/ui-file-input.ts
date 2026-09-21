import { Component, ElementRef, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let nextId = 0;

@Component({
  selector: 'ui-file-input',
  templateUrl: './ui-file-input.html',
  styleUrl: './ui-file-input.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => UiFileInput),
      multi: true,
    },
  ],
})
export class UiFileInput implements ControlValueAccessor {
  readonly id = `ui-file-input-${nextId++}`;
  readonly errorId = `${this.id}-error`;
  readonly label = input('');
  readonly accept = input('');
  readonly errorMessage = input<string | null>(null);
  readonly noFileLabel = input('No file selected');

  private readonly nativeInput = viewChild<ElementRef<HTMLInputElement>>('input');

  readonly fileName = signal<string | null>(null);
  readonly disabled = signal(false);

  private onChange: (value: File | null) => void = () => {};
  private onTouched: () => void = () => {};

  /**
   * Un <input type="file"> no admite que se le asigne un valor por programación (restricción
   * de seguridad del navegador): writeValue solo puede limpiar el campo visualmente cuando
   * value es null (p.ej. al resetear el formulario tras una subida con éxito), nunca
   * "rellenarlo" con un File concreto.
   */
  writeValue(value: File | null): void {
    if (value === null) {
      this.fileName.set(null);
      const inputEl = this.nativeInput()?.nativeElement;
      if (inputEl) {
        inputEl.value = '';
      }
    }
  }

  registerOnChange(fn: (value: File | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  handleChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.fileName.set(file?.name ?? null);
    this.onChange(file);
  }

  handleBlur(): void {
    this.onTouched();
  }
}
