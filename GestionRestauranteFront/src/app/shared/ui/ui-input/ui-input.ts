import { Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { UiIcon } from '../ui-icon/ui-icon';

let nextId = 0;

@Component({
  selector: 'ui-input',
  imports: [UiIcon],
  templateUrl: './ui-input.html',
  styleUrl: './ui-input.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => UiInput),
      multi: true,
    },
  ],
})
export class UiInput implements ControlValueAccessor {
  readonly id = `ui-input-${nextId++}`;
  readonly label = input('');
  readonly type = input('text');
  readonly placeholder = input('');
  readonly errorMessage = input<string | null>(null);
  readonly min = input('');
  readonly max = input('');
  readonly required = input(false);
  readonly autocomplete = input('');
  // Defaults en ingles solo como fallback tecnico: en type="password" hay que pasar siempre
  // estos dos traducidos (p.ej. 'common.showPassword'/'common.hidePassword'), o el aria-label
  // del boton de mostrar/ocultar sale en ingles en una app que es español-primero.
  readonly showPasswordLabel = input('Show password');
  readonly hidePasswordLabel = input('Hide password');

  readonly value = signal('');
  readonly disabled = signal(false);

  readonly showPassword = signal(false);
  readonly isPassword = computed(() => this.type() === 'password');
  readonly effectiveType = computed(() => (this.isPassword() && this.showPassword() ? 'text' : this.type()));

  togglePasswordVisibility(): void {
    this.showPassword.update((value) => !value);
  }

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  handleInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.value.set(value);
    this.onChange(value);
  }

  handleBlur(): void {
    this.onTouched();
  }
}
