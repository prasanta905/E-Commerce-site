import api from './api';
import type { ApiResponse } from '../types';

export interface RazorpaySetup {
  gatewayOrderId: string;
  keyId: string;
  amount: number;
  currency: string;
  orderNumber: string;
  paymentStatus: string;
}

export interface VerifyResult {
  orderStatus: string;
  paymentStatus: string;
}

export const paymentService = {
  /** Loads the Razorpay checkout.js SDK once (idempotent across retries). */
  loadCheckoutScript(): Promise<boolean> {
    const w = window as unknown as { Razorpay?: unknown };
    if (w.Razorpay) return Promise.resolve(true);
    return new Promise((resolve) => {
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  },

  /** Resolve (or reuse) the Razorpay gateway order for a PENDING order. */
  createRazorpayOrder: async (orderId: number): Promise<RazorpaySetup> => {
    const res = await api.post<ApiResponse<RazorpaySetup>>(
      `/payments/orders/${orderId}/create`
    );
    return res.data.data;
  },

  /** Verify the checkout handshake — HMAC of gatewayOrderId|paymentId. */
  verify: async (payload: {
    razorpayOrderId: string;
    razorpayPaymentId: string;
    razorpaySignature: string;
  }): Promise<VerifyResult> => {
    const res = await api.post<ApiResponse<VerifyResult>>('/payments/verify', payload);
    return res.data.data;
  },
};
