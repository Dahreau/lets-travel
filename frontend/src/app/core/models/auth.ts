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

// registrationToken vient de RegistrationResponse (POST /api/users/register, appele en 1er).
export interface RegisterRequest {
  username: string;
  password: string;
  registrationToken: string;
}
