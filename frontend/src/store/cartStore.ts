import { create } from 'zustand';
import { Cart } from '../types';
import api from '../services/api';

interface CartState {
  cart: Cart | null;
  itemCount: number;
  isLoading: boolean;
  fetchCart: () => Promise<void>;
  addItem: (productId: number, quantity?: number) => Promise<void>;
  updateQuantity: (itemId: number, quantity: number) => Promise<void>;
  removeItem: (itemId: number) => Promise<void>;
  clearCart: () => Promise<void>;
  resetCart: () => void;
}

export const useCartStore = create<CartState>((set, get) => ({
  cart: null,
  itemCount: 0,
  isLoading: false,

  fetchCart: async () => {
    try {
      set({ isLoading: true });
      const res = await api.get('/cart');
      if (res.data?.success && res.data.data) {
        const cart: Cart = res.data.data;
        const count = cart.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
        set({ cart, itemCount: count });
      }
    } catch {
      // unauthenticated or empty cart is normal
    } finally {
      set({ isLoading: false });
    }
  },

  addItem: async (productId: number, quantity = 1) => {
    try {
      set({ isLoading: true });
      const res = await api.post('/cart/items', { productId, quantity });
      if (res.data?.success && res.data.data) {
        const cart: Cart = res.data.data;
        const count = cart.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
        set({ cart, itemCount: count });
      }
    } finally {
      set({ isLoading: false });
    }
  },

  updateQuantity: async (itemId: number, quantity: number) => {
    try {
      set({ isLoading: true });
      if (quantity <= 0) {
        await get().removeItem(itemId);
        return;
      }
      const res = await api.put(`/cart/items/${itemId}`, { quantity });
      if (res.data?.success && res.data.data) {
        const cart: Cart = res.data.data;
        const count = cart.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
        set({ cart, itemCount: count });
      }
    } finally {
      set({ isLoading: false });
    }
  },

  removeItem: async (itemId: number) => {
    try {
      set({ isLoading: true });
      const res = await api.delete(`/cart/items/${itemId}`);
      if (res.data?.success && res.data.data) {
        const cart: Cart = res.data.data;
        const count = cart.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
        set({ cart, itemCount: count });
      }
    } finally {
      set({ isLoading: false });
    }
  },

  clearCart: async () => {
    try {
      set({ isLoading: true });
      await api.delete('/cart');
      set({ cart: { items: [], subtotal: 0, itemCount: 0 }, itemCount: 0 });
    } finally {
      set({ isLoading: false });
    }
  },

  resetCart: () => {
    set({ cart: null, itemCount: 0 });
  },
}));
