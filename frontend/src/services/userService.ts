import api from './api';
import type { Address } from '../types';

export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  emailVerified: boolean;
}

export interface AddressPayload {
  fullName: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country?: string;
  isDefault?: boolean;
}

export const userService = {
  getProfile: async (): Promise<UserProfile> => {
    const res = await api.get<{ success: boolean; data: UserProfile }>('/users/me');
    return res.data.data;
  },

  updateProfile: async (payload: { fullName: string; phone?: string }): Promise<UserProfile> => {
    const res = await api.put<{ success: boolean; data: UserProfile }>('/users/me', payload);
    return res.data.data;
  },

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await api.post('/users/me/password', { currentPassword, newPassword });
  },

  getAddresses: async (): Promise<Address[]> => {
    const res = await api.get<{ success: boolean; data: Address[] }>('/users/me/addresses');
    return res.data.data || [];
  },

  addAddress: async (address: AddressPayload): Promise<Address> => {
    const res = await api.post<{ success: boolean; data: Address }>('/users/me/addresses', address);
    return res.data.data;
  },

  updateAddress: async (id: number, address: AddressPayload): Promise<Address> => {
    const res = await api.put<{ success: boolean; data: Address }>(`/users/me/addresses/${id}`, address);
    return res.data.data;
  },

  deleteAddress: async (id: number): Promise<void> => {
    await api.delete(`/users/me/addresses/${id}`);
  },
};
