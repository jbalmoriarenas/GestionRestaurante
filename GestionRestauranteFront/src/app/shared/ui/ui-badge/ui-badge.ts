import { Component, input } from '@angular/core';

export type BadgeVariant = 'success' | 'warning' | 'danger' | 'neutral';

@Component({
  selector: 'ui-badge',
  template: '<span class="ui-badge" [class]="variant()"><ng-content /></span>',
  styleUrl: './ui-badge.scss',
})
export class UiBadge {
  readonly variant = input<BadgeVariant>('neutral');
}
