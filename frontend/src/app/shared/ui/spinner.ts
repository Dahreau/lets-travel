import { Component } from '@angular/core';

@Component({
  selector: 'app-spinner',
  template: `
    <span class="spinner" role="status" aria-label="Chargement">
      <span class="spin">[</span>&nbsp;<span class="blink">loading</span>&nbsp;<span class="spin">]</span>
    </span>
  `,
  styles: `
    .spinner {
      display: inline-flex;
      align-items: center;
      color: var(--text-muted);
      font-size: 12px;
    }
  `,
})
export class Spinner {}
