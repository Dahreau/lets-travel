import { Component, input } from '@angular/core';
import { Travel } from '../../core/models/travel';
import { Badge } from './badge';

// Carte resume dates/prix/statut/destinations partagee entre ManagerTravelDetail et TravelDetail
// (troubleshooting.md #77) - lecture seule, aucun formulaire implique.
@Component({
  selector: 'app-travel-summary-card',
  imports: [Badge],
  template: `
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">dates</div>
        <div class="stat-value" style="font-size: 16px">{{ travel().startDate }} → {{ travel().endDate }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">prix</div>
        <div class="stat-value">
          {{ travel().price !== null ? travel().price + ' ' + travel().currency : 'gratuit' }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">statut</div>
        <div class="stat-value"><app-badge [value]="travel().status" /></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">destinations</div>
        <div class="stat-value">{{ travel().destinations.length }}</div>
      </div>
    </div>
  `,
})
export class TravelSummaryCard {
  readonly travel = input.required<Travel>();
}
