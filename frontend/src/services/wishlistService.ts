import api from './api';
import type { ProductCard } from '../types';

export interface WishlistData {
  products: ProductCard[];
}

export const wishlistService = {
  getWishlist: async (): Promise<ProductCard[]> => {
    const res = await api.get<{ success: boolean; data: WishlistData }>('/wishlist');
    return res.data.data?.products || [];
  },

  addToWishlist: async (productId: number): Promise<ProductCard[]> => {
    const res = await api.post<{ success: boolean; data: WishlistData }>('/wishlist/items', { productId });
    return res.data.data?.products || [];
  },

  removeFromWishlist: async (productId: number): Promise<void> => {
    await api.delete(`/wishlist/items/${productId}`);
  },
};
