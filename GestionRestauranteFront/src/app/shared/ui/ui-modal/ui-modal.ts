import { Component, ElementRef, HostListener, effect, input, output, viewChild } from '@angular/core';

@Component({
  selector: 'ui-modal',
  templateUrl: './ui-modal.html',
  styleUrl: './ui-modal.scss',
})
export class UiModal {
  readonly open = input(false);
  readonly title = input('');
  readonly closeLabel = input('Close');
  readonly closed = output<void>();

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private previouslyFocused: HTMLElement | null = null;

  constructor() {
    // Al abrir: guarda el elemento con foco y lo mueve dentro del modal.
    // Al cerrar: devuelve el foco a quien lo abrio, para no perder el contexto de teclado.
    effect(() => {
      if (this.open()) {
        this.previouslyFocused = document.activeElement as HTMLElement;
        queueMicrotask(() => this.panel()?.nativeElement.focus());
      } else if (this.previouslyFocused) {
        this.previouslyFocused.focus();
        this.previouslyFocused = null;
      }
    });
  }

  @HostListener('document:keydown.escape')
  handleEscape(): void {
    if (this.open()) {
      this.closed.emit();
    }
  }

  handleBackdropClick(): void {
    this.closed.emit();
  }
}
