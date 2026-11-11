import api from './api';
import { ApiResponse, AuthResponse } from '../types';

export const authService = {
  login: async (email: string, password: string):Promise<AuthResponse> => {
    const res = await api.post<ApiResponse<AuthResponse>>('/auth/login', { email, password });
    return res.data.data;
  },

  register: async (payload: { fullName: string; email: string; password: string; phone?: string }): Promise<AuthResponse> => {
    const res = await api.post<ApiResponse<AuthResponse>>('/auth/register', payload);
    return res.data.data;
  },

  logout: async (): Promise<void> => {
    try {
      await api.post('/auth/logout');
    } catch {
      // ignore
    }
  },

  forgotPassword: async (email: string): Promise<string> => {
    const res = await api.post<ApiResponse<{ message: string }>>('/auth/forgot-password', { email });
    return res.data.data?.message || 'Password reset link sent';
  },

  getProfile: async () => {
    const res = await api.get('/users/me');
    return res.data.data;
  },

  getAddresses: async () => {
    const res = await api.get('/users/me/addresses');
    return res.data.data;
  },

  addAddress: async (payload: { contactName: string; phone: string; line1: string; line2?: string; city: string; state: string; pincode: string; isDefault?: boolean }) => {
    const res = await api.post('/users/me/addresses', payload);
    return res.data.data;
  },

  deleteAddress: async (id: number) => {
    const res = await api.delete(`/users/me/addresses/${id}`);
    return res.data.data;
  },
};
