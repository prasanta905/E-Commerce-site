import React, { useEffect, useState } from 'react';
import {
  RotateCcw,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Clock,
  Search,
} from 'lucide-react';
import { adminService } from '../../services/adminService';
import type { AdminReturn } from '../../types';
import LoadingSpinner from '../../components/LoadingSpinner';

const AdminReturns: React.FC = () => {
  const [returns, setReturns] = useState<AdminReturn[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [processingId, setProcessingId] = useState<number | null>(null);

  const fetchReturns = async (p = 0) => {
    try {
      setLoading(true);
      const res = await adminService.getReturns(undefined, p, 15);
      setReturns(res.items || []);
      setTotalPages(res.meta?.totalPages || 1);
    } catch (err) {
      console.error('Failed to load returns', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReturns(page);
  }, [page]);

  const handleUpdate = async (id: number, status: 'APPROVED' | 'REJECTED') => {
    try {
      setProcessingId(id);
      await adminService.updateReturnStatus(
        id,
        status,
        status === 'APPROVED' ? 'Return verified & refund processed.' : 'Item condition does not meet return policy.'
      );
      setReturns((prev) =>
        prev.map((r) => (r.id === id ? { ...r, status } : r))
      );
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to update return status');
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <div className="space-y-6 animate-fade-in">
      <div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
          Returns & Reverse Logistics
        </h1>
        <p className="text-slate-500 text-sm mt-1">
          Review RMA return requests, inspect causes, and issue automated merchant refunds
        </p>
      </div>

      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm overflow-hidden">
        {loading && returns.length === 0 ? (
          <div className="p-12">
            <LoadingSpinner text="Retrieving return requests..." />
          </div>
        ) : returns.length === 0 ? (
          <div className="p-12 text-center text-slate-500 text-sm">
            No return requests currently logged.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-xs font-bold uppercase tracking-wider text-slate-500">
                  <th className="py-4 px-6">Return ID</th>
                  <th className="py-4 px-6">Order ID</th>
                  <th className="py-4 px-6">Reason</th>
                  <th className="py-4 px-6">Status</th>
                  <th className="py-4 px-6">Requested At</th>
                  <th className="py-4 px-6 text-right">Review Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {returns.map((ret) => (
                  <tr key={ret.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-4 px-6 font-mono font-bold text-purple-700">
                      RMA #{ret.id}
                    </td>
                    <td className="py-4 px-6 font-mono font-medium text-slate-800">
                      #{ret.orderId}
                    </td>
                    <td className="py-4 px-6 text-xs text-slate-600 max-w-xs">
                      {ret.reason}
                    </td>
                    <td className="py-4 px-6">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          ret.status === 'APPROVED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : ret.status === 'REJECTED'
                            ? 'bg-rose-100 text-rose-800'
                            : 'bg-amber-100 text-amber-800'
                        }`}
                      >
                        {ret.status}
                      </span>
                    </td>
                    <td className="py-4 px-6 text-xs text-slate-500">
                      {new Date(ret.createdAt).toLocaleDateString('en-IN', {
                        dateStyle: 'medium',
                      })}
                    </td>
                    <td className="py-4 px-6 text-right">
                      {ret.status === 'REQUESTED' || ret.status === 'PENDING' ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handleUpdate(ret.id, 'APPROVED')}
                            disabled={processingId === ret.id}
                            className="px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold transition disabled:opacity-50"
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => handleUpdate(ret.id, 'REJECTED')}
                            disabled={processingId === ret.id}
                            className="px-3 py-1.5 rounded-xl border border-rose-200 text-rose-600 hover:bg-rose-50 text-xs font-semibold transition disabled:opacity-50"
                          >
                            Reject
                          </button>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">Processed</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

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
    </div>
  );
};

export default AdminReturns;
