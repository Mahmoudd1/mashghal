import { UserRole } from '../models/api.models';

export interface AuthenticatedUser {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: AuthenticatedUser;
}
