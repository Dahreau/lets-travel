import { Component, computed, input } from '@angular/core';

export type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const VARIANT_BY_VALUE: Record<string, BadgeVariant> = {
  ADMIN: 'info',
  TRAVELER: 'neutral',
  PLANNED: 'info',
  CONFIRMED: 'success',
  CANCELLED: 'danger',
  COMPLETED: 'neutral',
  PENDING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  REFUNDED: 'neutral',
  STRIPE: 'info',
  PAYPAL: 'info',
};

@Component({
  selector: 'app-badge',
  template: `<span class="badge" [class]="'badge-' + variant()">{{ value() }}</span>`,
})
export class Badge {
  readonly value = input.required<string>();
  readonly variantOverride = input<BadgeVariant | undefined>(undefined, { alias: 'variant' });

  protected readonly variant = computed<BadgeVariant>(
    () => this.variantOverride() ?? VARIANT_BY_VALUE[this.value()] ?? 'neutral',
  );
}
