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
  ownerId: string;
  startDate: string;
  endDate: string;
  durationDays: number;
  status: TravelStatus;
  destinations: Destination[];
  transportations: Transportation[];
  createdAt: string;
  updatedAt: string;
}

export interface TravelRequest {
  title: string;
  ownerId: string;
  startDate: string;
  endDate: string;
  status: TravelStatus;
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
