export type ProviderType = 'STRIPE' | 'PAYPAL';
export type MethodType = 'CARD' | 'PAYPAL_ACCOUNT';
export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';

export interface PaymentMethod {
  id: string;
  ownerId: string;
  provider: ProviderType;
  type: MethodType;
  brand: string | null;
  last4: string | null;
  isDefault: boolean;
  createdAt: string;
}

export interface PaymentMethodRequest {
  ownerId: string;
  provider: ProviderType;
  type: MethodType;
  providerToken: string;
  brand: string | null;
  last4: string | null;
  isDefault: boolean;
}

export interface Payment {
  id: string;
  travelId: string;
  ownerId: string;
  paymentMethodId: string | null;
  amount: number;
  currency: string;
  provider: ProviderType;
  status: PaymentStatus;
  providerReference: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentRequest {
  travelId: string;
  ownerId: string;
  paymentMethodId: string;
  amount: number;
  currency: string;
}

export const PROVIDER_TYPES: ProviderType[] = ['STRIPE', 'PAYPAL'];
export const METHOD_TYPES: MethodType[] = ['CARD', 'PAYPAL_ACCOUNT'];
