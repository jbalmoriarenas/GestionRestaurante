import { Component, input, output } from '@angular/core';

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

  handleBackdropClick(): void {
    this.closed.emit();
  }
}
