export type UserRole = 'TRAVELER' | 'ADMIN';

export interface Address {
  street: string;
  city: string;
  postalCode: string;
  country: string;
}

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: UserRole;
  address: Address | null;
  createdAt: string;
  updatedAt: string;
}

export interface UserRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: UserRole;
  address: Address | null;
}
