import api from './api';
import {
  AdminDashboard,
  AdminAnalytics,
  AdminProduct,
  AdminInventoryItem,
  AdminCustomer,
  AdminCoupon,
  AdminReturn,
  OrderSummary,
  OrderDetail,
  PageResponse,
  Category,
  Brand,
} from '../types';

export const adminService = {
  getDashboard: async (): Promise<AdminDashboard> => {
    const res = await api.get<{ success: boolean; data: AdminDashboard }>('/admin/dashboard');
    return res.data.data;
  },

  getAnalytics: async (period = 'daily'): Promise<AdminAnalytics> => {
    const res = await api.get<{ success: boolean; data: AdminAnalytics }>('/admin/analytics', {
      params: { period },
    });
    return res.data.data;
  },

  getOrders: async (status?: string, search?: string, page = 0, size = 15) => {
    const res = await api.get<{
      success: boolean;
      data: { items: OrderSummary[]; meta: PageResponse };
    }>('/admin/orders', { params: { status, search, page, size } });
    return res.data.data;
  },

  getOrderDetail: async (id: number): Promise<OrderDetail> => {
    const res = await api.get<{ success: boolean; data: OrderDetail }>(`/admin/orders/${id}`);
    return res.data.data;
  },

  updateOrderStatus: async (id: number, status: string, note?: string) => {
    const res = await api.put(`/admin/orders/${id}/status`, { status, note });
    return res.data.data;
  },

  getProducts: async (search?: string, page = 0, size = 20) => {
    const res = await api.get<{
      success: boolean;
      data: { items: AdminProduct[]; meta: PageResponse };
    }>('/admin/products', { params: { search, page, size } });
    return res.data.data;
  },

  createProduct: async (payload: {
    name: string;
    description?: string;
    sellingPrice: number;
    mrp: number;
    categoryId: number;
    brandId: number;
    isFeatured?: boolean;
    imageUrl?: string;
    initialStock?: number;
    lowStockThreshold?: number;
  }): Promise<AdminProduct> => {
    const res = await api.post<{ success: boolean; data: AdminProduct }>('/admin/products', payload);
    return res.data.data;
  },

  updateProduct: async (
    id: number,
    payload: {
      name?: string;
      description?: string;
      sellingPrice?: number;
      mrp?: number;
      categoryId?: number;
      brandId?: number;
      isFeatured?: boolean;
      isActive?: boolean;
    }
  ): Promise<AdminProduct> => {
    const res = await api.put<{ success: boolean; data: AdminProduct }>(`/admin/products/${id}`, payload);
    return res.data.data;
  },

  deleteProduct: async (id: number) => {
    const res = await api.delete(`/admin/products/${id}`);
    return res.data.data;
  },

  toggleProductActive: async (id: number, active: boolean) => {
    const res = await api.patch(`/admin/products/${id}/active`, { active });
    return res.data.data;
  },

  getCategories: async (): Promise<Category[]> => {
    const res = await api.get<{ success: boolean; data: Category[] }>('/admin/categories');
    return res.data.data;
  },

  createCategory: async (payload: { name: string; slug?: string; imageUrl?: string; parentId?: number }) => {
    const res = await api.post('/admin/categories', payload);
    return res.data.data;
  },

  deleteCategory: async (id: number) => {
    const res = await api.delete(`/admin/categories/${id}`);
    return res.data.data;
  },

  getBrands: async (): Promise<Brand[]> => {
    const res = await api.get<{ success: boolean; data: Brand[] }>('/admin/brands');
    return res.data.data;
  },

  createBrand: async (payload: { name: string; slug?: string; logoUrl?: string }) => {
    const res = await api.post('/admin/brands', payload);
    return res.data.data;
  },

  getCustomers: async (q?: string, active?: boolean, page = 0, size = 20) => {
    const res = await api.get<{
      success: boolean;
      data: { items: AdminCustomer[]; meta: PageResponse };
    }>('/admin/customers', { params: { q, active, page, size } });
    return res.data.data;
  },

  toggleCustomerActive: async (id: number, active: boolean) => {
    const res = await api.put(`/admin/customers/${id}/active`, { active });
    return res.data.data;
  },

  getInventory: async (lowOnly = false): Promise<AdminInventoryItem[]> => {
    const res = await api.get<{ success: boolean; data: AdminInventoryItem[] }>('/admin/inventory', {
      params: { lowOnly },
    });
    return res.data.data;
  },

  adjustStock: async (productId: number, delta: number, reason?: string): Promise<AdminInventoryItem> => {
    const res = await api.put<{ success: boolean; data: AdminInventoryItem }>(`/admin/inventory/${productId}`, {
      delta,
      reason,
    });
    return res.data.data;
  },

  getCoupons: async (): Promise<AdminCoupon[]> => {
    const res = await api.get<{ success: boolean; data: AdminCoupon[] }>('/admin/coupons');
    return res.data.data;
  },

  createCoupon: async (payload: {
    code: string;
    discountType: 'PERCENT' | 'FIXED';
    discountValue: number;
    minOrderAmount?: number;
    maxDiscountAmount?: number;
    expiresAt: string;
    maxGlobalUses?: number;
    maxUsesPerUser?: number;
  }): Promise<AdminCoupon> => {
    const res = await api.post<{ success: boolean; data: AdminCoupon }>('/admin/coupons', payload);
    return res.data.data;
  },

  deleteCoupon: async (id: number) => {
    const res = await api.delete(`/admin/coupons/${id}`);
    return res.data.data;
  },

  getReturns: async (status?: string, page = 0, size = 15) => {
    const res = await api.get<{
      success: boolean;
      data: { items: AdminReturn[]; meta: PageResponse };
    }>('/admin/returns', { params: { status, page, size } });
    return res.data.data;
  },

  updateReturnStatus: async (id: number, status: string, note?: string) => {
    const res = await api.put(`/admin/returns/${id}/status`, { status, note });
    return res.data.data;
  },
};
