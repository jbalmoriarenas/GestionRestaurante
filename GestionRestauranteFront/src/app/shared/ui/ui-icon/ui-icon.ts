import { Component, input } from '@angular/core';

export type IconName =
  | 'empleados'
  | 'fichajes'
  | 'vacaciones'
  | 'facturas'
  | 'nominas'
  | 'valoraciones'
  | 'configuracion'
  | 'logout'
  | 'menu'
  | 'chevron'
  | 'eye'
  | 'eye-off'
  | 'sun'
  | 'moon';

@Component({
  selector: 'ui-icon',
  templateUrl: './ui-icon.html',
  styleUrl: './ui-icon.scss',
})
export class UiIcon {
  readonly name = input.required<IconName>();
}
