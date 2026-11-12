import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import {
  ArrowLeft,
  Package,
  Truck,
  MapPin,
  Clock,
  AlertCircle,
  CheckCircle2,
  XCircle,
  RotateCcw,
  QrCode,
} from 'lucide-react';
import { orderService } from '../services/orderService';
import { paymentService } from '../services/paymentService';
import type { OrderDetail as OrderDetailType } from '../types';
import LoadingSpinner from '../components/LoadingSpinner';

const ORDER_STEPS = [
  'PENDING',
  'CONFIRMED',
  'PROCESSING',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

const OrderDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [order, setOrder] = useState<OrderDetailType | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Cancellation & Return modals
  const [showCancelModal, setShowCancelModal] = useState<boolean>(false);
  const [showReturnModal, setShowReturnModal] = useState<boolean>(false);
  const [reason, setReason] = useState<string>('');
  const [actionLoading, setActionLoading] = useState<boolean>(false);
  const [payLoading, setPayLoading] = useState<boolean>(false);
  const [payError, setPayError] = useState<string | null>(
    (location.state as { paymentError?: string } | null)?.paymentError ?? null
  );

  const returnedFromYoupi = searchParams.get('payment') === 'youpi';

  const fetchOrderDetail = async () => {
    if (!id) return;
    try {
      setLoading(true);
      setError(null);
      const data = await orderService.getOrderDetail(Number(id));
      setOrder(data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load order details.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrderDetail();
  }, [id]);

  /** Resume payment for a PENDING online order (Razorpay modal or YouPI redirect). */
  const handleCompletePayment = async () => {
    if (!order) return;
    try {
      setPayLoading(true);
      setPayError(null);

      if (order.paymentMethod === 'UPI') {
        // Server creates/reuses the hosted YouPI session; only the signed
        // RECONCILED webhook can settle the order after the redirect.
        const session = await paymentService.createYoupiSession(order.id);
        window.location.assign(session.checkoutUrl);
        return;
      }

      const setup = await paymentService.createRazorpayOrder(order.id);
      const loaded = await paymentService.loadCheckoutScript();
      const RazorpayCtor = (window as unknown as {
        Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
      }).Razorpay;
      if (!loaded || !RazorpayCtor || !setup.keyId) {
        setPayError('Payment gateway could not be loaded. Please try again later.');
        return;
      }
      const checkout = new RazorpayCtor({
        key: setup.keyId,
        amount: Math.round(setup.amount * 100),
        currency: setup.currency,
        name: 'ShopSphere',
        description: `Order ${setup.orderNumber}`,
        order_id: setup.gatewayOrderId,
        theme: { color: '#4f46e5' },
        handler: async (response: {
          razorpay_order_id: string;
          razorpay_payment_id: string;
          razorpay_signature: string;
        }) => {
          try {
            await paymentService.verify({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            fetchOrderDetail();
          } catch (verifyErr: any) {
            setPayError(
              verifyErr.response?.data?.message ||
                'Payment verification failed. Contact support with your order number.'
            );
          } finally {
            setPayLoading(false);
          }
        },
        modal: {
          ondismiss: () => {
            setPayError('Payment window closed before completion. Your order is still awaiting payment.');
            setPayLoading(false);
          },
        },
      });
      checkout.open();
    } catch (err: any) {
      const code = err.response?.data?.code;
      const message = err.response?.data?.message;
      setPayError(
        code === 'YOUPI_NOT_CONFIGURED'
          ? 'UPI payment is not configured on this environment. Please choose Cash on Delivery on a new order.'
          : message || 'Could not start online payment. Please try again later.'
      );
      setPayLoading(false);
    }
  };

  const handleCancel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !reason.trim()) return;
    try {
      setActionLoading(true);
      await orderService.cancelOrder(Number(id), reason.trim());
      setShowCancelModal(false);
      setReason('');
      fetchOrderDetail();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Cancellation failed.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !reason.trim()) return;
    try {
      setActionLoading(true);
      await orderService.requestReturn(Number(id), reason.trim());
      setShowReturnModal(false);
      setReason('');
      fetchOrderDetail();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Return request failed.');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return <LoadingSpinner fullPage text="Retrieving order details..." />;
  }

  if (error || !order) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <AlertCircle className="w-12 h-12 text-rose-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-slate-800 mb-2">Order Not Found</h2>
        <p className="text-slate-600 text-sm mb-6">{error || 'Unable to retrieve this order.'}</p>
        <button
          onClick={() => navigate('/orders')}
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 text-white font-medium text-sm hover:bg-indigo-700"
        >
          <ArrowLeft className="w-4 h-4" /> Back to My Orders
        </button>
      </div>
    );
  }

  const currentStepIndex = ORDER_STEPS.indexOf(order.status);
  const isCancelled = order.status === 'CANCELLED';
  const isReturned = order.status === 'RETURNED' || order.status === 'RETURN_REQUESTED';
  const isCancellable = order.status === 'PENDING' || order.status === 'CONFIRMED';
  const isReturnable = order.status === 'DELIVERED';
  const isAwaitingPayment =
    order.status === 'PENDING' &&
    (order.paymentMethod === 'RAZORPAY' || order.paymentMethod === 'UPI');

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      {/* Header Back & Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <Link
          to="/orders"
          className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600 hover:text-indigo-600 transition"
        >
          <ArrowLeft className="w-4 h-4" /> Back to All Orders
        </Link>
        <div className="flex items-center gap-3">
          {isCancellable && (
            <button
              onClick={() => setShowCancelModal(true)}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-rose-600 border border-rose-200 hover:bg-rose-50 transition"
            >
              Cancel Order
            </button>
          )}
          {isReturnable && (
            <button
              onClick={() => setShowReturnModal(true)}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-purple-600 border border-purple-200 hover:bg-purple-50 transition flex items-center gap-1.5"
            >
              <RotateCcw className="w-3.5 h-3.5" /> Return Items
            </button>
          )}
        </div>
      </div>

      {/* YouPI return banner: the redirect is never proof of payment. */}
      {returnedFromYoupi && (
        <div
          className={`mb-6 p-5 rounded-2xl border flex flex-wrap items-start justify-between gap-3 ${
            order.status === 'PENDING'
              ? 'border-sky-200 bg-sky-50'
              : order.status === 'CANCELLED'
                ? 'border-slate-200 bg-slate-50'
                : 'border-emerald-200 bg-emerald-50'
          }`}
        >
          <div className="flex items-start gap-3">
            {order.status === 'PENDING' ? (
              <QrCode className="w-5 h-5 text-sky-600 mt-0.5" />
            ) : order.status === 'CANCELLED' ? (
              <AlertCircle className="w-5 h-5 text-slate-500 mt-0.5" />
            ) : (
              <CheckCircle2 className="w-5 h-5 text-emerald-600 mt-0.5" />
            )}
            <div>
              <p
                className={`text-sm font-semibold ${
                  order.status === 'PENDING'
                    ? 'text-sky-900'
                    : order.status === 'CANCELLED'
                      ? 'text-slate-700'
                      : 'text-emerald-900'
                }`}
              >
                {order.status === 'PENDING'
                  ? 'UPI payment submitted — awaiting bank reconciliation'
                  : order.status === 'CANCELLED'
                    ? 'This order was cancelled'
                    : 'UPI payment confirmed'}
              </p>
              <p className="text-xs text-slate-600 mt-1 max-w-2xl">
                {order.status === 'PENDING'
                  ? 'YouPI confirms an order only after the gateway matches a reconciled bank settlement. This page does not mark the order paid on its own — you can safely close it and check back later.'
                  : order.status === 'CANCELLED'
                    ? 'If money left your account for a cancelled order, contact support with the UTR before retrying payment.'
                    : 'The gateway reported a reconciled UPI settlement and the order has moved forward.'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={fetchOrderDetail}
              className="px-4 py-2 rounded-xl border border-slate-300 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
            >
              Refresh status
            </button>
            <button
              type="button"
              onClick={() => setSearchParams({}, { replace: true })}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-500 hover:text-slate-700 transition"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      {/* Pending payment banner */}
      {isAwaitingPayment && (
        <div className="mb-6 p-5 rounded-2xl border border-amber-200 bg-amber-50 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-amber-600 mt-0.5" />
            <div>
              <p className="text-sm font-semibold text-amber-900">Payment pending</p>
              <p className="text-xs text-amber-800">
                {order.paymentMethod === 'UPI'
                  ? 'This UPI order is still awaiting a reconciled bank settlement. Retry the hosted checkout if you have not paid yet.'
                  : 'This order was placed with online payment but the payment is not complete yet.'}
              </p>
              {payError && (
                <p className="text-xs text-rose-600 mt-1">{payError}</p>
              )}
            </div>
          </div>
          <button
            onClick={handleCompletePayment}
            disabled={payLoading}
            className="px-5 py-2.5 rounded-xl bg-amber-600 text-white text-sm font-semibold hover:bg-amber-700 disabled:opacity-60 transition"
          >
            {payLoading
              ? 'Opening payment…'
              : order.paymentMethod === 'UPI'
                ? 'Pay with UPI'
                : 'Complete Payment'}
          </button>
        </div>
      )}

      {/* Main Order Card */}
      <div className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden mb-8">
        {/* Top Info Banner */}
        <div className="p-6 sm:p-8 bg-slate-50/60 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-xl sm:text-2xl font-bold text-slate-900 font-mono">
                {order.orderNumber}
              </h1>
              <span
                className={`px-3 py-1 rounded-full text-xs font-bold ${
                  order.status === 'DELIVERED'
                    ? 'bg-emerald-100 text-emerald-800'
                    : order.status === 'CANCELLED'
                    ? 'bg-rose-100 text-rose-800'
                    : order.status === 'RETURNED' || order.status === 'RETURN_REQUESTED'
                    ? 'bg-purple-100 text-purple-800'
                    : 'bg-indigo-100 text-indigo-800'
                }`}
              >
                {order.status.replace(/_/g, ' ')}
              </span>
            </div>
            <p className="text-xs text-slate-500">
              Placed on {new Date(order.createdAt).toLocaleString('en-IN', {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}
            </p>
          </div>

          <div className="text-right">
            <span className="text-xs text-slate-500 block">Total Amount</span>
            <span className="text-2xl font-extrabold text-slate-900">
              ₹{order.grandTotal.toLocaleString('en-IN')}
            </span>
          </div>
        </div>

        {/* Tracking Progress Bar (if active order) */}
        {!isCancelled && !isReturned && (
          <div className="p-6 sm:p-8 border-b border-slate-100">
            <h3 className="text-sm font-semibold text-slate-800 mb-6 flex items-center gap-2">
              <Truck className="w-4 h-4 text-indigo-600" /> Delivery Progress
            </h3>

            <div className="grid grid-cols-2 sm:grid-cols-6 gap-2">
              {ORDER_STEPS.map((step, idx) => {
                const isPassed = currentStepIndex >= idx;
                const isCurrent = currentStepIndex === idx;

                return (
                  <div key={step} className="flex flex-col items-center text-center">
                    <div
                      className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs mb-2 transition ${
                        isPassed
                          ? 'bg-indigo-600 text-white shadow-sm'
                          : 'bg-slate-100 text-slate-400'
                      } ${isCurrent ? 'ring-4 ring-indigo-100' : ''}`}
                    >
                      {isPassed ? <CheckCircle2 className="w-4 h-4" /> : idx + 1}
                    </div>
                    <span
                      className={`text-[11px] font-medium leading-tight ${
                        isCurrent
                          ? 'text-indigo-600 font-bold'
                          : isPassed
                          ? 'text-slate-700'
                          : 'text-slate-400'
                      }`}
                    >
                      {step.replace(/_/g, ' ')}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {isCancelled && (
          <div className="p-6 bg-rose-50/50 border-b border-rose-100 flex items-center gap-3 text-rose-800">
            <XCircle className="w-5 h-5 flex-shrink-0 text-rose-600" />
            <div className="text-sm">
              <span className="font-semibold">This order has been cancelled.</span> Stock reservation
              has been restored and refunds have been triggered to your original payment method.
            </div>
          </div>
        )}

        {/* Order Details Body */}
        <div className="p-6 sm:p-8 grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Items List (2 cols) */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <Package className="w-5 h-5 text-indigo-600" /> Ordered Items ({order.items.length})
            </h3>

            <div className="divide-y divide-slate-100 border border-slate-200/80 rounded-2xl overflow-hidden">
              {order.items.map((item) => (
                <div key={item.id} className="p-4 flex items-center justify-between gap-4 bg-white hover:bg-slate-50/50 transition">
                  <div className="flex items-center gap-4">
                    <div className="w-16 h-16 rounded-xl bg-slate-50 border border-slate-100 flex-shrink-0 overflow-hidden flex items-center justify-center">
                      {item.productImage ? (
                        <img
                          src={item.productImage}
                          alt={item.productName}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <Package className="w-8 h-8 text-slate-300" />
                      )}
                    </div>
                    <div>
                      <Link
                        to={`/products/${item.productId}`}
                        className="text-sm font-semibold text-slate-800 hover:text-indigo-600 transition line-clamp-1"
                      >
                        {item.productName}
                      </Link>
                      <p className="text-xs text-slate-500 mt-1">
                        Qty: {item.quantity} × ₹{item.unitPrice.toLocaleString('en-IN')}
                      </p>
                    </div>
                  </div>
                  <span className="text-base font-bold text-slate-900 whitespace-nowrap">
                    ₹{(item.totalPrice || item.lineTotal || (item.unitPrice * item.quantity)).toLocaleString('en-IN')}
                  </span>
                </div>
              ))}
            </div>

            {/* Status History (if present) */}
            {order.statusHistory && order.statusHistory.length > 0 && (
              <div className="mt-8">
                <h4 className="text-sm font-bold text-slate-800 mb-3 flex items-center gap-2">
                  <Clock className="w-4 h-4 text-slate-500" /> Status History
                </h4>
                <div className="bg-slate-50 rounded-2xl p-4 border border-slate-200/60 space-y-2">
                  {order.statusHistory.map((hist, i) => (
                    <div key={i} className="flex items-center justify-between text-xs py-1">
                      <span className="font-semibold text-slate-700">{hist.toStatus}</span>
                      <span className="text-slate-500">
                        {new Date(hist.createdAt).toLocaleString('en-IN', {
                          dateStyle: 'short',
                          timeStyle: 'short',
                        })}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Right Column: Address & Payment Summary */}
          <div className="space-y-6">
            {/* Delivery Address */}
            {order.shippingAddress && (
              <div className="bg-slate-50 rounded-2xl p-5 border border-slate-200/70">
                <h4 className="text-sm font-bold text-slate-800 mb-2 flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-indigo-600" /> Delivery Address
                </h4>
                <p className="text-sm font-medium text-slate-800">{order.shippingAddress.fullName}</p>
                <p className="text-xs text-slate-600 mt-1">{order.shippingAddress.line1}</p>
                {order.shippingAddress.line2 && (
                  <p className="text-xs text-slate-600">{order.shippingAddress.line2}</p>
                )}
                <p className="text-xs text-slate-600">
                  {order.shippingAddress.city}, {order.shippingAddress.state} - {order.shippingAddress.postalCode}
                </p>
                <p className="text-xs text-slate-500 mt-2 font-mono">
                  Phone: {order.shippingAddress.phone}
                </p>
              </div>
            )}

            {/* Price Breakdown */}
            <div className="bg-slate-50 rounded-2xl p-5 border border-slate-200/70 space-y-3">
              <h4 className="text-sm font-bold text-slate-800 pb-2 border-b border-slate-200">
                Payment Summary
              </h4>

              <div className="flex justify-between text-xs text-slate-600">
                <span>Subtotal</span>
                <span>₹{order.subtotal.toLocaleString('en-IN')}</span>
              </div>

              <div className="flex justify-between text-xs text-slate-600">
                <span>Shipping</span>
                <span>{order.shippingFee === 0 ? 'FREE' : `₹${order.shippingFee.toLocaleString('en-IN')}`}</span>
              </div>

              {order.discountAmount > 0 && (
                <div className="flex justify-between text-xs text-emerald-600 font-semibold">
                  <span>Discount</span>
                  <span>-₹{order.discountAmount.toLocaleString('en-IN')}</span>
                </div>
              )}

              <div className="pt-2 border-t border-slate-200 flex justify-between text-base font-bold text-slate-900">
                <span>Total Paid</span>
                <span>₹{order.grandTotal.toLocaleString('en-IN')}</span>
              </div>

              <div className="pt-2 text-xs text-slate-500">
                <span>Payment Status: </span>
                <span className="font-semibold text-slate-700">{order.paymentStatus}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Cancel Modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-slate-900 mb-2">Confirm Cancellation</h3>
            <p className="text-xs text-slate-500 mb-4">
              Are you sure you want to cancel this order? Stock will be restored immediately.
            </p>
            <form onSubmit={handleCancel} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Reason for Cancellation
                </label>
                <textarea
                  rows={3}
                  required
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Tell us why you are cancelling..."
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none resize-none"
                />
              </div>
              <div className="flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowCancelModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Close
                </button>
                <button
                  type="submit"
                  disabled={actionLoading || !reason.trim()}
                  className="px-5 py-2 text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 rounded-xl disabled:opacity-50"
                >
                  {actionLoading ? 'Cancelling...' : 'Confirm Cancellation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Return Modal */}
      {showReturnModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-slate-900 mb-2">Request Return</h3>
            <p className="text-xs text-slate-500 mb-4">
              Submit a return request for admin review. Please describe the problem with the item.
            </p>
            <form onSubmit={handleReturn} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Reason for Return
                </label>
                <textarea
                  rows={3}
                  required
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="e.g. Defective, incorrect color or size..."
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:outline-none resize-none"
                />
              </div>
              <div className="flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowReturnModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Close
                </button>
                <button
                  type="submit"
                  disabled={actionLoading || !reason.trim()}
                  className="px-5 py-2 text-xs font-semibold text-white bg-purple-600 hover:bg-purple-700 rounded-xl disabled:opacity-50"
                >
                  {actionLoading ? 'Submitting...' : 'Submit Request'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default OrderDetail;
