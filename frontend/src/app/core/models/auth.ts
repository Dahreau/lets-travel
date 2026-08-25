export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
}

export interface MeResponse {
  username: string;
  role: string;
}

// feat/traveler-frontend : 2e etape de l'inscription publique (POST /api/auth/register),
// userId vient de la reponse de UserRegistrationRequest (user-service, appele en 1er).
export interface RegisterRequest {
  username: string;
  password: string;
  userId: string;
}
