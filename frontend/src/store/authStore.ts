import { create } from 'zustand';
import { User } from '../types';
import { authService } from '../services/authService';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (payload: { fullName: string; email: string; password: string; phone?: string }) => Promise<void>;
  logout: () => void;
  setUser: (user: User) => void;
  updateUser: (user: Partial<User>) => void;
}

const getStoredUser = (): User | null => {
  try {
    const raw = localStorage.getItem('shopsphere_user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

const initialToken = localStorage.getItem('shopsphere_access_token');
const initialUser = getStoredUser();

export const useAuthStore = create<AuthState>((set) => ({
  user: initialUser,
  token: initialToken,
  isAuthenticated: !!initialToken && !!initialUser,
  isAdmin: initialUser?.role === 'ROLE_ADMIN',

  login: async (email, password) => {
    const res = await authService.login(email, password);
    const user: User = {
      id: 0, // placeholder until profile is fetched
      email: res.email,
      fullName: res.fullName,
      role: res.role,
      emailVerified: res.emailVerified,
    };
    localStorage.setItem('shopsphere_access_token', res.accessToken);
    localStorage.setItem('shopsphere_user', JSON.stringify(user));
    set({
      user,
      token: res.accessToken,
      isAuthenticated: true,
      isAdmin: res.role === 'ROLE_ADMIN',
    });
  },

  register: async (payload) => {
    const res = await authService.register(payload);
    const user: User = {
      id: 0,
      email: res.email,
      fullName: res.fullName,
      role: res.role,
      emailVerified: res.emailVerified,
    };
    localStorage.setItem('shopsphere_access_token', res.accessToken);
    localStorage.setItem('shopsphere_user', JSON.stringify(user));
    set({
      user,
      token: res.accessToken,
      isAuthenticated: true,
      isAdmin: res.role === 'ROLE_ADMIN',
    });
  },

  setUser: (user) => {
    localStorage.setItem('shopsphere_user', JSON.stringify(user));
    set({
      user,
      isAdmin: user.role === 'ROLE_ADMIN',
    });
  },

  logout: () => {
    authService.logout().catch(() => {});
    localStorage.removeItem('shopsphere_access_token');
    localStorage.removeItem('shopsphere_user');
    set({
      user: null,
      token: null,
      isAuthenticated: false,
      isAdmin: false,
    });
  },

  updateUser: (updated) => {
    set((state) => {
      if (!state.user) return state;
      const newUser = { ...state.user, ...updated };
      localStorage.setItem('shopsphere_user', JSON.stringify(newUser));
      return {
        user: newUser,
        isAdmin: newUser.role === 'ROLE_ADMIN',
      };
    });
  },
}));
