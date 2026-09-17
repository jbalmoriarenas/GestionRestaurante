import { Component, input } from '@angular/core';

@Component({
  selector: 'ui-spinner',
  template: '<span class="ui-spinner" [attr.aria-label]="label()"></span>',
  styleUrl: './ui-spinner.scss',
})
export class UiSpinner {
  readonly label = input('Loading');
}
