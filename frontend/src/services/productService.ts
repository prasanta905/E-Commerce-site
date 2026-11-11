import api from './api';
import { ProductCard, ProductDetail, Category, Brand, Review, PageResponse } from '../types';

export interface ProductFilterParams {
  search?: string;
  categoryId?: number;
  brandId?: number;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  inStock?: boolean;
  sort?: string;
  page?: number;
  size?: number;
}

export const productService = {
  getProducts: async (params?: ProductFilterParams) => {
    const res = await api.get<{
      success: boolean;
      data: { items: ProductCard[]; meta: PageResponse };
    }>('/products', { params });
    return res.data.data;
  },

  getFeatured: async (page = 0, size = 8) => {
    const res = await api.get<{
      success: boolean;
      data: { items: ProductCard[]; meta: PageResponse };
    }>('/products/featured', { params: { page, size } });
    return res.data.data.items;
  },

  getNewArrivals: async (page = 0, size = 8) => {
    const res = await api.get<{
      success: boolean;
      data: { items: ProductCard[]; meta: PageResponse };
    }>('/products/new-arrivals', { params: { page, size } });
    return res.data.data.items;
  },

  getBestSellers: async (page = 0, size = 8) => {
    const res = await api.get<{
      success: boolean;
      data: { items: ProductCard[]; meta: PageResponse };
    }>('/products/best-sellers', { params: { page, size } });
    return res.data.data.items;
  },

  getProductById: async (id: number): Promise<ProductDetail> => {
    const res = await api.get<{ success: boolean; data: ProductDetail }>(`/products/${id}`);
    return res.data.data;
  },

  getSearchSuggestions: async (q: string): Promise<ProductCard[]> => {
    if (!q || q.length < 2) return [];
    const res = await api.get<{ success: boolean; data: ProductCard[] }>('/products/search', { params: { q } });
    return res.data.data || [];
  },

  getCategories: async (): Promise<Category[]> => {
    const res = await api.get<{ success: boolean; data: Category[] }>('/categories');
    return res.data.data || [];
  },

  getBrands: async (): Promise<Brand[]> => {
    const res = await api.get<{ success: boolean; data: Brand[] }>('/brands');
    return res.data.data || [];
  },

  getReviews: async (productId: number): Promise<Review[]> => {
    const res = await api.get<{ success: boolean; data: { items: Review[] } }>(`/products/${productId}/reviews`);
    return res.data.data?.items || [];
  },

  addReview: async (productId: number, payload: { rating: number; title: string; body: string }) => {
    const res = await api.post(`/products/${productId}/reviews`, payload);
    return res.data.data;
  },
};
