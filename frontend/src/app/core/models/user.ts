export type UserRole = 'TRAVELER' | 'TRAVEL_MANAGER' | 'ADMIN';

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

// feat/traveler-frontend : 1ere etape de l'inscription publique (POST /api/users/register),
// pas de champ "role" - toujours force a TRAVELER cote serveur (UserService.register).
export interface UserRegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  address: Address | null;
}
