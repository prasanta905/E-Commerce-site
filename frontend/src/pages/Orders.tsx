import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, ArrowRight, Clock, AlertCircle, X, RotateCcw } from 'lucide-react';
import { orderService } from '../services/orderService';
import type { OrderSummary } from '../types';
import LoadingSpinner from '../components/LoadingSpinner';

const Orders: React.FC = () => {
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);

  // Modal state
  const [actionModal, setActionModal] = useState<{
    type: 'cancel' | 'return';
    orderId: number;
    orderNumber: string;
  } | null>(null);
  const [actionReason, setActionReason] = useState<string>('');
  const [actionSubmitting, setActionSubmitting] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<{ text: string; isError: boolean } | null>(null);

  const fetchOrders = async (pageIndex = 0) => {
    try {
      setLoading(true);
      setError(null);
      const res = await orderService.getOrders(pageIndex, 10);
      setOrders(res.items || []);
      setTotalPages(res.meta.totalPages || 1);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load your orders.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(page);
  }, [page]);

  const handleActionSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!actionModal || !actionReason.trim()) return;

    try {
      setActionSubmitting(true);
      if (actionModal.type === 'cancel') {
        await orderService.cancelOrder(actionModal.orderId, actionReason.trim());
        setActionMessage({ text: 'Order cancelled successfully.', isError: false });
      } else {
        await orderService.requestReturn(actionModal.orderId, actionReason.trim());
        setActionMessage({ text: 'Return request submitted for admin review.', isError: false });
      }
      setTimeout(() => {
        setActionModal(null);
        setActionReason('');
        setActionMessage(null);
        fetchOrders(page);
      }, 1200);
    } catch (err: any) {
      setActionMessage({
        text: err.response?.data?.message || 'Operation failed. Please try again.',
        isError: true,
      });
    } finally {
      setActionSubmitting(false);
    }
  };

  const getStatusBadge = (status: string) => {
    const s = status.toUpperCase();
    if (s === 'DELIVERED') {
      return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">Delivered</span>;
    }
    if (s === 'CANCELLED') {
      return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800">Cancelled</span>;
    }
    if (s === 'RETURNED' || s === 'RETURN_REQUESTED') {
      return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-800">{s === 'RETURN_REQUESTED' ? 'Return Requested' : 'Returned'}</span>;
    }
    if (s === 'SHIPPED' || s === 'OUT_FOR_DELIVERY') {
      return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">{s.replace(/_/g, ' ')}</span>;
    }
    return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">{s}</span>;
  };

  if (loading && orders.length === 0) {
    return <LoadingSpinner fullPage text="Loading your orders..." />;
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">My Orders</h1>
          <p className="text-slate-500 text-sm mt-1">Review tracking, item summaries, and order statuses</p>
        </div>
        <Link
          to="/products"
          className="inline-flex items-center gap-2 text-sm font-semibold text-indigo-600 hover:text-indigo-700"
        >
          Explore Catalog <ArrowRight className="w-4 h-4" />
        </Link>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 text-rose-700 rounded-2xl flex items-center gap-3 mb-6">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {orders.length === 0 ? (
        <div className="bg-white rounded-3xl p-12 text-center border border-slate-100 shadow-sm max-w-lg mx-auto">
          <div className="w-16 h-16 bg-slate-50 text-slate-400 rounded-full flex items-center justify-center mx-auto mb-4">
            <Package className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-slate-800 mb-2">No orders placed yet</h2>
          <p className="text-slate-500 text-sm mb-6">Explore our curated collection and place your first order!</p>
          <Link
            to="/products"
            className="inline-flex items-center justify-center px-6 py-3 rounded-xl bg-indigo-600 text-white font-semibold text-sm hover:bg-indigo-700 shadow-sm transition"
          >
            Start Shopping
          </Link>
        </div>
      ) : (
        <div className="space-y-6">
          {orders.map((order) => {
            const isCancellable = order.status === 'PENDING' || order.status === 'CONFIRMED';
            const isReturnable = order.status === 'DELIVERED';

            return (
              <div
                key={order.id}
                className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden hover:border-slate-300 transition"
              >
                {/* Order Header */}
                <div className="bg-slate-50/70 px-6 py-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4">
                  <div className="flex flex-wrap items-center gap-4 sm:gap-8 text-xs sm:text-sm">
                    <div>
                      <span className="text-slate-400 block font-medium">Order Number</span>
                      <span className="font-mono font-bold text-slate-800">{order.orderNumber}</span>
                    </div>
                    <div>
                      <span className="text-slate-400 block font-medium">Date Placed</span>
                      <span className="text-slate-700 flex items-center gap-1 font-medium">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        {new Date(order.createdAt).toLocaleDateString('en-IN', {
                          day: 'numeric',
                          month: 'short',
                          year: 'numeric',
                        })}
                      </span>
                    </div>
                    <div>
                      <span className="text-slate-400 block font-medium">Total</span>
                      <span className="font-bold text-slate-900">₹{order.grandTotal.toLocaleString('en-IN')}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    {getStatusBadge(order.status)}
                    <Link
                      to={`/orders/${order.id}`}
                      className="px-3.5 py-1.5 rounded-lg border border-slate-300 text-xs font-semibold text-slate-700 hover:bg-white hover:border-indigo-600 hover:text-indigo-600 transition"
                    >
                      View Details
                    </Link>
                  </div>
                </div>

                {/* Items & Actions */}
                <div className="p-6">
                  <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
                    <div className="space-y-2">
                      <p className="text-sm font-semibold text-slate-800">
                        {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'} in this order
                      </p>
                      <p className="text-xs text-slate-500">
                        Payment Status: <span className="font-semibold text-slate-700">{order.paymentStatus}</span>
                      </p>
                    </div>

                    <div className="flex items-center gap-3">
                      {isCancellable && (
                        <button
                          onClick={() => setActionModal({ type: 'cancel', orderId: order.id, orderNumber: order.orderNumber })}
                          className="px-4 py-2 rounded-xl text-xs font-semibold text-rose-600 border border-rose-200 hover:bg-rose-50 transition"
                        >
                          Cancel Order
                        </button>
                      )}
                      {isReturnable && (
                        <button
                          onClick={() => setActionModal({ type: 'return', orderId: order.id, orderNumber: order.orderNumber })}
                          className="px-4 py-2 rounded-xl text-xs font-semibold text-purple-600 border border-purple-200 hover:bg-purple-50 transition flex items-center gap-1.5"
                        >
                          <RotateCcw className="w-3.5 h-3.5" /> Return Items
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-6">
              {Array.from({ length: totalPages }).map((_, idx) => (
                <button
                  key={idx}
                  onClick={() => setPage(idx)}
                  className={`w-9 h-9 rounded-lg text-sm font-semibold transition ${
                    page === idx
                      ? 'bg-indigo-600 text-white shadow-sm'
                      : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  {idx + 1}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Action Modal (Cancel / Return) */}
      {actionModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl animate-fade-in relative">
            <button
              onClick={() => {
                setActionModal(null);
                setActionReason('');
                setActionMessage(null);
              }}
              className="absolute top-5 right-5 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-bold text-slate-900 mb-1">
              {actionModal.type === 'cancel' ? 'Cancel Order' : 'Request Return'}
            </h3>
            <p className="text-xs text-slate-500 mb-4">
              Order: <span className="font-mono font-semibold text-slate-700">{actionModal.orderNumber}</span>
            </p>

            {actionMessage ? (
              <div
                className={`p-4 rounded-xl text-sm font-medium ${
                  actionMessage.isError ? 'bg-rose-50 text-rose-700' : 'bg-emerald-50 text-emerald-700'
                }`}
              >
                {actionMessage.text}
              </div>
            ) : (
              <form onSubmit={handleActionSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    {actionModal.type === 'cancel' ? 'Reason for Cancellation' : 'Reason for Return'}
                  </label>
                  <textarea
                    rows={3}
                    required
                    value={actionReason}
                    onChange={(e) => setActionReason(e.target.value)}
                    placeholder={
                      actionModal.type === 'cancel'
                        ? 'e.g., Ordered by mistake, found better price, delivery delay...'
                        : 'e.g., Defective item, size does not match, arrived damaged...'
                    }
                    className="w-full text-sm px-3 py-2 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => {
                      setActionModal(null);
                      setActionReason('');
                    }}
                    className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition"
                  >
                    Close
                  </button>
                  <button
                    type="submit"
                    disabled={actionSubmitting || !actionReason.trim()}
                    className={`px-5 py-2 text-xs font-semibold text-white rounded-xl shadow-sm transition disabled:opacity-50 ${
                      actionModal.type === 'cancel'
                        ? 'bg-rose-600 hover:bg-rose-700'
                        : 'bg-purple-600 hover:bg-purple-700'
                    }`}
                  >
                    {actionSubmitting ? 'Submitting...' : 'Confirm'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default Orders;
