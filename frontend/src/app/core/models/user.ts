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
  // fix/audit-gaps (troubleshooting.md #41) : date du consentement RGPD donne a l'inscription
  // publique - null pour les profils crees par un ADMIN (voir UserResponse cote backend).
  privacyAcceptedAt: string | null;
}

export interface UserRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: UserRole;
  address: Address | null;
  // fix/audit-gaps : optionnels - permettent de provisionner un compte de connexion en meme
  // temps que le profil (voir UserForm), jamais utilises pour role=ADMIN.
  username?: string;
  password?: string;
}

// feat/traveler-frontend : 1ere etape de l'inscription publique (POST /api/users/register),
// pas de champ "role" - toujours force a TRAVELER cote serveur (UserService.register).
export interface UserRegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  address: Address | null;
  // voir troubleshooting.md #41 - consentement RGPD obligatoire, @AssertTrue cote backend.
  acceptedPrivacyPolicy: boolean;
}

export interface RegistrationResponse {
  user: User;
  registrationToken: string;
}
