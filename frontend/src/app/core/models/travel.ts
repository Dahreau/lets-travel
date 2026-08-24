export type TravelStatus = 'PLANNED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
export type AccommodationType = 'HOTEL' | 'HOSTEL' | 'APARTMENT' | 'RESORT' | 'OTHER';
export type TransportationType = 'FLIGHT' | 'TRAIN' | 'BUS' | 'CAR' | 'BOAT' | 'OTHER';

export interface Activity {
  id?: string;
  name: string;
  description: string | null;
  date: string;
  cost: number | null;
}

export interface Accommodation {
  id?: string;
  name: string;
  type: AccommodationType;
  address: string;
  checkIn: string;
  checkOut: string;
}

export interface Destination {
  id?: string;
  city: string;
  country: string;
  arrivalDate: string;
  departureDate: string;
  orderIndex: number;
  activities: Activity[];
  accommodation: Accommodation | null;
}

export interface Transportation {
  id?: string;
  type: TransportationType;
  fromLocation: string;
  toLocation: string;
  departureTime: string;
  arrivalTime: string;
  provider: string | null;
}

export interface Travel {
  id: string;
  title: string;
  // Anciennement "ownerId" cote frontend (stale depuis feat/travel-manager-role, qui a renomme
  // le champ backend) : un voyage est une offre creee et geree par un Travel Manager, pas la
  // propriete d'un traveler (qui s'y abonne via Subscription - voir docs/nouveautes-vs-travel-plan.md).
  managerId: string;
  startDate: string;
  endDate: string;
  durationDays: number;
  status: TravelStatus;
  // Nullable : les voyages crees avant feat/travel-pricing-and-traveler-payment n'en ont pas
  // retroactivement. TravelRequest, lui, les impose desormais pour toute creation/modification.
  price: number | null;
  currency: string | null;
  destinations: Destination[];
  transportations: Transportation[];
  createdAt: string;
  updatedAt: string;
}

export interface TravelRequest {
  title: string;
  managerId: string;
  startDate: string;
  endDate: string;
  status: TravelStatus;
  price: number;
  currency: string;
  destinations: Destination[];
  transportations: Transportation[];
}

export const TRAVEL_STATUSES: TravelStatus[] = ['PLANNED', 'CONFIRMED', 'CANCELLED', 'COMPLETED'];
export const ACCOMMODATION_TYPES: AccommodationType[] = [
  'HOTEL',
  'HOSTEL',
  'APARTMENT',
  'RESORT',
  'OTHER',
];
export const TRANSPORTATION_TYPES: TransportationType[] = [
  'FLIGHT',
  'TRAIN',
  'BUS',
  'CAR',
  'BOAT',
  'OTHER',
];
