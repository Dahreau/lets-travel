export type SubscriptionStatus = 'ACTIVE' | 'CANCELLED';

export interface Subscription {
  id: string;
  travelId: string;
  travelerId: string;
  status: SubscriptionStatus;
  subscribedAt: string;
  cancelledAt: string | null;
}
