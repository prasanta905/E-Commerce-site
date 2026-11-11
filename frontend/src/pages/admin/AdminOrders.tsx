import React, { useEffect, useState } from 'react';
import {
  ShoppingBag,
  Search,
  Filter,
  CheckCircle2,
  Clock,
  Eye,
  X,
  AlertCircle,
  Truck,
  Package,
} from 'lucide-react';
import { adminService } from '../../services/adminService';
import type { OrderSummary, OrderDetail } from '../../types';
import LoadingSpinner from '../../components/LoadingSpinner';

const STATUS_OPTIONS = [
  'ALL',
  'PENDING',
  'CONFIRMED',
  'PROCESSING',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'RETURN_REQUESTED',
  'RETURNED',
];

const AdminOrders: React.FC = () => {
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [search, setSearch] = useState<string>('');
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);

  // Detail & Transition Modal State
  const [selectedOrder, setSelectedOrder] = useState<OrderDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);
  const [newStatus, setNewStatus] = useState<string>('');
  const [statusNote, setStatusNote] = useState<string>('');
  const [updating, setUpdating] = useState<boolean>(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const fetchOrders = async (p = 0, stat = statusFilter, q = search) => {
    try {
      setLoading(true);
      const res = await adminService.getOrders(
        stat === 'ALL' ? undefined : stat,
        q.trim() || undefined,
        p,
        15
      );
      setOrders(res.items || []);
      setTotalPages(res.meta?.totalPages || 1);
    } catch (err) {
      console.error('Failed to load orders', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(page, statusFilter, search);
  }, [page, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchOrders(0, statusFilter, search);
  };

  const openOrderDetail = async (orderId: number) => {
    try {
      setDetailLoading(true);
      setModalError(null);
      const detail = await adminService.getOrderDetail(orderId);
      setSelectedOrder(detail);
      setNewStatus(detail.status);
      setStatusNote('');
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to fetch order details');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleStatusUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOrder || !newStatus || newStatus === selectedOrder.status) return;

    try {
      setUpdating(true);
      setModalError(null);
      await adminService.updateOrderStatus(selectedOrder.id, newStatus, statusNote.trim() || undefined);
      // Reload detail and orders list
      const updated = await adminService.getOrderDetail(selectedOrder.id);
      setSelectedOrder(updated);
      fetchOrders(page, statusFilter, search);
    } catch (err: any) {
      setModalError(err.response?.data?.message || 'Invalid state transition for this order.');
    } finally {
      setUpdating(false);
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
      return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-800">{s.replace(/_/g, ' ')}</span>;
    }
    if (s === 'SHIPPED' || s === 'OUT_FOR_DELIVERY') {
      return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">{s.replace(/_/g, ' ')}</span>;
    }
    return <span className="px-3 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">{s}</span>;
  };

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            Order Fulfillment
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Track customer orders, advance fulfillment state machine, and handle returns
          </p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white rounded-3xl p-4 border border-slate-200/80 shadow-sm flex flex-col md:flex-row items-center justify-between gap-4">
        <form onSubmit={handleSearchSubmit} className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by order #..."
            className="w-full pl-10 pr-4 py-2 text-sm rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </form>

        <div className="flex items-center gap-2 overflow-x-auto w-full md:w-auto pb-2 md:pb-0">
          <Filter className="w-4 h-4 text-slate-400 flex-shrink-0" />
          {STATUS_OPTIONS.map((status) => (
            <button
              key={status}
              onClick={() => {
                setStatusFilter(status);
                setPage(0);
              }}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition ${
                statusFilter === status
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {status.replace(/_/g, ' ')}
            </button>
          ))}
        </div>
      </div>

      {/* Orders Table */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm overflow-hidden">
        {loading && orders.length === 0 ? (
          <div className="p-12">
            <LoadingSpinner text="Retrieving order records..." />
          </div>
        ) : orders.length === 0 ? (
          <div className="p-12 text-center text-slate-500 text-sm">
            No orders found matching the filter criteria.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-xs font-bold uppercase tracking-wider text-slate-500">
                  <th className="py-4 px-6">Order #</th>
                  <th className="py-4 px-6">Date</th>
                  <th className="py-4 px-6">Items</th>
                  <th className="py-4 px-6">Grand Total</th>
                  <th className="py-4 px-6">Status</th>
                  <th className="py-4 px-6 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.map((ord) => (
                  <tr key={ord.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-4 px-6 font-mono font-bold text-indigo-700">
                      {ord.orderNumber}
                    </td>
                    <td className="py-4 px-6 text-xs text-slate-500">
                      {new Date(ord.createdAt).toLocaleString('en-IN', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      })}
                    </td>
                    <td className="py-4 px-6 text-slate-700 font-medium">
                      {ord.itemCount} {ord.itemCount === 1 ? 'item' : 'items'}
                    </td>
                    <td className="py-4 px-6 font-bold text-slate-900">
                      ₹{ord.grandTotal.toLocaleString('en-IN')}
                    </td>
                    <td className="py-4 px-6">{getStatusBadge(ord.status)}</td>
                    <td className="py-4 px-6 text-right">
                      <button
                        onClick={() => openOrderDetail(ord.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 hover:border-indigo-600 hover:text-indigo-600 text-xs font-semibold text-slate-700 transition"
                      >
                        <Eye className="w-3.5 h-3.5" /> Manage
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-slate-100 flex items-center justify-center gap-2">
            {Array.from({ length: totalPages }).map((_, i) => (
              <button
                key={i}
                onClick={() => setPage(i)}
                className={`w-8 h-8 rounded-lg text-xs font-semibold transition ${
                  page === i
                    ? 'bg-indigo-600 text-white'
                    : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
                }`}
              >
                {i + 1}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Order Detail & Transition Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-3xl max-w-2xl w-full p-6 sm:p-8 shadow-2xl animate-fade-in max-h-[90vh] overflow-y-auto relative">
            <button
              onClick={() => setSelectedOrder(null)}
              className="absolute top-6 right-6 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-2">
              <h3 className="text-xl font-bold text-slate-900 font-mono">
                {selectedOrder.orderNumber}
              </h3>
              {getStatusBadge(selectedOrder.status)}
            </div>
            <p className="text-xs text-slate-400 mb-6">
              Placed on {new Date(selectedOrder.createdAt).toLocaleString('en-IN')}
            </p>

            {modalError && (
              <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs mb-4 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                {modalError}
              </div>
            )}

            {/* State Transition Form */}
            <form onSubmit={handleStatusUpdate} className="bg-slate-50 border border-slate-200/80 rounded-2xl p-5 mb-6 space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Advance Fulfillment State
              </h4>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Transition Status
                  </label>
                  <select
                    value={newStatus}
                    onChange={(e) => setNewStatus(e.target.value)}
                    className="w-full text-xs font-semibold px-3 py-2 rounded-xl border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    {STATUS_OPTIONS.filter((s) => s !== 'ALL').map((st) => (
                      <option key={st} value={st}>
                        {st.replace(/_/g, ' ')}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Note / Tracking Number
                  </label>
                  <input
                    type="text"
                    value={statusNote}
                    onChange={(e) => setStatusNote(e.target.value)}
                    placeholder="e.g. Courier tracking AWB#..."
                    className="w-full text-xs px-3 py-2 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>

              <div className="pt-2 flex justify-end">
                <button
                  type="submit"
                  disabled={updating || newStatus === selectedOrder.status}
                  className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs transition disabled:opacity-50"
                >
                  {updating ? 'Updating Status...' : 'Apply Transition'}
                </button>
              </div>
            </form>

            {/* Items */}
            <div className="space-y-3 mb-6">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Items ({selectedOrder.items?.length || 0})
              </h4>
              <div className="divide-y divide-slate-100 border border-slate-200/70 rounded-2xl overflow-hidden max-h-48 overflow-y-auto">
                {selectedOrder.items?.map((item) => (
                  <div key={item.id} className="p-3 flex items-center justify-between text-xs bg-white">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-slate-50 border border-slate-200 overflow-hidden flex items-center justify-center flex-shrink-0">
                        {item.productImage ? (
                          <img src={item.productImage} alt={item.productName} className="w-full h-full object-cover" />
                        ) : (
                          <Package className="w-5 h-5 text-slate-400" />
                        )}
                      </div>
                      <div>
                        <p className="font-semibold text-slate-800 line-clamp-1">{item.productName}</p>
                        <p className="text-slate-400">Qty: {item.quantity} × ₹{item.unitPrice.toLocaleString('en-IN')}</p>
                      </div>
                    </div>
                    <span className="font-bold text-slate-900">
                      ₹{(item.totalPrice || item.lineTotal || (item.unitPrice * item.quantity)).toLocaleString('en-IN')}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* Address & Total */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs pt-4 border-t border-slate-100">
              <div>
                <span className="font-semibold text-slate-700 block mb-1">Delivery Address:</span>
                {selectedOrder.shippingAddress && (
                  <p className="text-slate-500">
                    {selectedOrder.shippingAddress.fullName}<br />
                    {selectedOrder.shippingAddress.line1}, {selectedOrder.shippingAddress.city}<br />
                    {selectedOrder.shippingAddress.state} - {selectedOrder.shippingAddress.postalCode}<br />
                    Phone: {selectedOrder.shippingAddress.phone}
                  </p>
                )}
              </div>

              <div className="text-right space-y-1">
                <p className="text-slate-500">Subtotal: ₹{selectedOrder.subtotal.toLocaleString('en-IN')}</p>
                <p className="text-slate-500">Shipping: ₹{selectedOrder.shippingFee.toLocaleString('en-IN')}</p>
                {selectedOrder.discountAmount > 0 && (
                  <p className="text-emerald-600 font-semibold">- Discount: ₹{selectedOrder.discountAmount.toLocaleString('en-IN')}</p>
                )}
                <p className="text-base font-extrabold text-slate-900 pt-1">
                  Grand Total: ₹{selectedOrder.grandTotal.toLocaleString('en-IN')}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminOrders;
