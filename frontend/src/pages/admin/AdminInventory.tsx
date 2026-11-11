import React, { useEffect, useState } from 'react';
import {
  Boxes,
  AlertTriangle,
  Plus,
  Minus,
  CheckCircle2,
  RefreshCw,
  Search,
  X,
  AlertCircle,
} from 'lucide-react';
import { adminService } from '../../services/adminService';
import type { AdminInventoryItem } from '../../types';
import LoadingSpinner from '../../components/LoadingSpinner';

const AdminInventory: React.FC = () => {
  const [items, setItems] = useState<AdminInventoryItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [lowOnly, setLowOnly] = useState<boolean>(false);
  const [search, setSearch] = useState<string>('');

  // Stock Adjustment Modal
  const [adjustItem, setAdjustItem] = useState<AdminInventoryItem | null>(null);
  const [delta, setDelta] = useState<number>(10);
  const [reason, setReason] = useState<string>('Supplier Restock Batch');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const fetchInventory = async (isLow = lowOnly) => {
    try {
      setLoading(true);
      const data = await adminService.getInventory(isLow);
      setItems(data);
    } catch (err) {
      console.error('Failed to load inventory', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInventory(lowOnly);
  }, [lowOnly]);

  const handleAdjustSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adjustItem) return;

    try {
      setSubmitting(true);
      setModalError(null);
      const updated = await adminService.adjustStock(adjustItem.productId, delta, reason);
      setItems((prev) =>
        prev.map((it) => (it.productId === adjustItem.productId ? updated : it))
      );
      setAdjustItem(null);
    } catch (err: any) {
      setModalError(err.response?.data?.message || 'Stock adjustment failed.');
    } finally {
      setSubmitting(false);
    }
  };

  const filteredItems = items.filter(
    (item) =>
      item.productName.toLowerCase().includes(search.toLowerCase()) ||
      item.sku.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            Inventory & Warehousing
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Real-time stock tracking, reserved quantities, and supply restock replenishment
          </p>
        </div>

        <button
          onClick={() => fetchInventory(lowOnly)}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold shadow-sm transition"
        >
          <RefreshCw className="w-3.5 h-3.5" /> Refresh Stock
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white rounded-3xl p-4 border border-slate-200/80 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by product name or SKU..."
            className="w-full pl-10 pr-4 py-2 text-sm rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          <button
            onClick={() => setLowOnly(false)}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
              !lowOnly
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            All Inventory ({items.length})
          </button>
          <button
            onClick={() => setLowOnly(true)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
              lowOnly
                ? 'bg-amber-600 text-white shadow-sm'
                : 'bg-amber-50 text-amber-700 hover:bg-amber-100'
            }`}
          >
            <AlertTriangle className="w-3.5 h-3.5" /> Low Stock Only
          </button>
        </div>
      </div>

      {/* Inventory Table */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-12">
            <LoadingSpinner text="Retrieving inventory records..." />
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="p-12 text-center text-slate-500 text-sm">
            No inventory items found matching your filters.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-xs font-bold uppercase tracking-wider text-slate-500">
                  <th className="py-4 px-6">SKU & Item</th>
                  <th className="py-4 px-6">Available Units</th>
                  <th className="py-4 px-6">Reserved in Carts</th>
                  <th className="py-4 px-6">Threshold</th>
                  <th className="py-4 px-6">Status</th>
                  <th className="py-4 px-6 text-right">Stock Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredItems.map((item) => {
                  const isOut = item.quantityAvailable <= 0;
                  const isLow = !isOut && item.quantityAvailable <= item.lowStockThreshold;

                  return (
                    <tr key={item.productId} className="hover:bg-slate-50/60 transition">
                      <td className="py-4 px-6">
                        <div className="font-semibold text-slate-900">{item.productName}</div>
                        <span className="font-mono text-xs text-slate-400">SKU: {item.sku}</span>
                      </td>

                      <td className="py-4 px-6">
                        <span
                          className={`font-mono font-bold text-base ${
                            isOut
                              ? 'text-rose-600'
                              : isLow
                              ? 'text-amber-600'
                              : 'text-slate-900'
                          }`}
                        >
                          {item.quantityAvailable} units
                        </span>
                      </td>

                      <td className="py-4 px-6 font-mono text-slate-500 text-xs">
                        {item.quantityReserved} units
                      </td>

                      <td className="py-4 px-6 font-mono text-slate-500 text-xs">
                        {item.lowStockThreshold} units
                      </td>

                      <td className="py-4 px-6">
                        {isOut ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800">
                            Out of Stock
                          </span>
                        ) : isLow ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
                            <AlertTriangle className="w-3 h-3" /> Low Stock
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                            <CheckCircle2 className="w-3 h-3" /> In Stock
                          </span>
                        )}
                      </td>

                      <td className="py-4 px-6 text-right">
                        <button
                          onClick={() => {
                            setAdjustItem(item);
                            setDelta(10);
                            setReason('Supplier Restock');
                            setModalError(null);
                          }}
                          className="px-3.5 py-1.5 rounded-xl border border-slate-200 hover:border-indigo-600 hover:text-indigo-600 text-xs font-semibold text-slate-700 transition"
                        >
                          Adjust Stock
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Adjust Stock Modal */}
      {adjustItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 sm:p-8 shadow-2xl animate-fade-in relative">
            <button
              onClick={() => setAdjustItem(null)}
              className="absolute top-6 right-6 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-xl font-bold text-slate-900 mb-1">Adjust Inventory</h3>
            <p className="text-xs text-slate-500 mb-4 line-clamp-1">{adjustItem.productName}</p>

            <div className="bg-slate-50 rounded-2xl p-4 border border-slate-200/70 mb-5 flex items-center justify-between text-xs">
              <div>
                <span className="text-slate-500 block">Current Available</span>
                <span className="font-mono text-lg font-bold text-slate-900">
                  {adjustItem.quantityAvailable} units
                </span>
              </div>
              <div>
                <span className="text-slate-500 block">Resulting Total</span>
                <span className="font-mono text-lg font-bold text-indigo-600">
                  {Math.max(0, adjustItem.quantityAvailable + Number(delta))} units
                </span>
              </div>
            </div>

            {modalError && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs mb-4 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                {modalError}
              </div>
            )}

            <form onSubmit={handleAdjustSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Quantity Adjustment (positive to add, negative to deduct)
                </label>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setDelta((d) => d - 10)}
                    className="p-2.5 rounded-xl border border-slate-200 hover:bg-slate-100 text-slate-600"
                  >
                    <Minus className="w-4 h-4" />
                  </button>
                  <input
                    type="number"
                    required
                    value={delta}
                    onChange={(e) => setDelta(Number(e.target.value))}
                    className="w-full text-center text-sm font-mono font-bold py-2 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                  <button
                    type="button"
                    onClick={() => setDelta((d) => d + 10)}
                    className="p-2.5 rounded-xl border border-slate-200 hover:bg-slate-100 text-slate-600"
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Reason / Adjustment Log
                </label>
                <input
                  type="text"
                  required
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="e.g. Purchase order shipment #1042, damaged goods write-off..."
                  className="w-full text-xs px-3 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setAdjustItem(null)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting || delta === 0}
                  className="px-5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl shadow-sm transition disabled:opacity-50"
                >
                  {submitting ? 'Applying...' : 'Confirm Stock Adjustment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminInventory;
