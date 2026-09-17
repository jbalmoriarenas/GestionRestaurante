import { Component, input } from '@angular/core';

export type IconName = 'empleados' | 'fichajes' | 'facturas' | 'nominas' | 'logout' | 'menu';

@Component({
  selector: 'ui-icon',
  templateUrl: './ui-icon.html',
  styleUrl: './ui-icon.scss',
})
export class UiIcon {
  readonly name = input.required<IconName>();
}
