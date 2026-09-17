import { Component, input, output } from '@angular/core';
import { UiSpinner } from '../ui-spinner/ui-spinner';

@Component({
  selector: 'ui-table',
  imports: [UiSpinner],
  templateUrl: './ui-table.html',
  styleUrl: './ui-table.scss',
})
export class UiTable {
  readonly loading = input(false);
  readonly empty = input(false);
  readonly emptyMessage = input('No data to display');
  readonly page = input(0);
  readonly totalPages = input(0);
  readonly previousLabel = input('← Previous');
  readonly nextLabel = input('Next →');
  readonly pageLabel = input('');
  readonly loadingLabel = input('Loading');
  readonly pageChange = output<number>();

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) {
      return;
    }
    this.pageChange.emit(page);
  }
}
