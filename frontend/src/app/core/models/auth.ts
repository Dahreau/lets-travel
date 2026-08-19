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
