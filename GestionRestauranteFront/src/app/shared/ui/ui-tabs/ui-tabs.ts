import { Component, input, output } from '@angular/core';

export interface UiTabItem {
  id: string;
  label: string;
}

@Component({
  selector: 'ui-tabs',
  templateUrl: './ui-tabs.html',
  styleUrl: './ui-tabs.scss',
})
export class UiTabs {
  readonly tabs = input<UiTabItem[]>([]);
  readonly activeTabId = input('');
  readonly tabChange = output<string>();

  selectTab(id: string): void {
    if (id !== this.activeTabId()) {
      this.tabChange.emit(id);
    }
  }
}
