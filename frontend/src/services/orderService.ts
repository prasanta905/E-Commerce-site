import api from './api';
import { OrderSummary, OrderDetail, CheckoutPreview, CouponApplication, PageResponse } from '../types';

export const orderService = {
  applyCoupon: async (code: string, subtotal: number): Promise<CouponApplication> => {
    const res = await api.get<{ success: boolean; data: CouponApplication }>('/coupons/apply', {
      params: { code, subtotal },
    });
    return res.data.data;
  },

  previewCheckout: async (payload: {
    addressId: number;
    shippingMethodId: number;
    couponCode?: string;
  }): Promise<CheckoutPreview> => {
    const res = await api.post<{ success: boolean; data: CheckoutPreview }>('/checkout/preview', payload);
    return res.data.data;
  },

  placeOrder: async (payload: {
    addressId: number;
    shippingMethodId: number;
    couponCode?: string;
    paymentMethod: string;
    idempotencyKey: string;
    termsAccepted: string;
  }): Promise<{ orderId: number; razorpayOrderId?: string }> => {
    const res = await api.post<{ success: boolean; data: { orderId: number; razorpayOrderId?: string } }>(
      '/checkout',
      payload
    );
    return res.data.data;
  },

  getShippingMethods: async (): Promise<
    Array<{ id: number; code: string; name: string; baseFee: number; freeAbove: number }>
  > => {
    const res = await api.get<{ success: boolean; data: Array<{ id: number; code: string; name: string; baseFee: number; freeAbove: number }> }>(
      '/checkout/shipping-methods'
    );
    return res.data.data;
  },

  getOrders: async (page = 0, size = 10) => {
    const res = await api.get<{
      success: boolean;
      data: { items: OrderSummary[]; meta: PageResponse };
    }>('/orders', { params: { page, size } });
    return res.data.data;
  },

  getOrderDetail: async (id: number): Promise<OrderDetail> => {
    const res = await api.get<{ success: boolean; data: OrderDetail }>(`/orders/${id}`);
    return res.data.data;
  },

  cancelOrder: async (id: number, reason: string) => {
    const res = await api.post(`/orders/${id}/cancel`, { reason });
    return res.data.data;
  },

  requestReturn: async (id: number, reason: string) => {
    const res = await api.post(`/orders/${id}/return-request`, { reason });
    return res.data.data;
  },
};
