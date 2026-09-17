import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { UiCard } from '../../shared/ui/ui-card/ui-card';

@Component({
  selector: 'app-fichajes-placeholder',
  imports: [UiCard, TranslatePipe],
  template: `
    <ui-card>
      <h1>{{ 'fichajes.title' | translate }}</h1>
      <p>{{ 'fichajes.placeholder' | translate }}</p>
    </ui-card>
  `,
})
export class FichajesPlaceholder {}
