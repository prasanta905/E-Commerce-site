import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { CheckCircle2, Package, ArrowRight, Truck, Home } from 'lucide-react';
import { orderService } from '../services/orderService';
import type { OrderDetail } from '../types';
import LoadingSpinner from '../components/LoadingSpinner';

const OrderConfirmation: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id || isNaN(Number(id))) {
      setLoading(false);
      return;
    }
    const fetchOrder = async () => {
      try {
        setLoading(true);
        const data = await orderService.getOrderDetail(Number(id));
        setOrder(data);
      } catch (err: any) {
        setError(err.response?.data?.message || 'Could not load order details.');
      } finally {
        setLoading(false);
      }
    };
    fetchOrder();
  }, [id]);

  if (loading) {
    return <LoadingSpinner fullPage text="Retrieving your order confirmation..." />;
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-8 sm:p-12 text-center">
        <div className="w-20 h-20 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-6 shadow-inner">
          <CheckCircle2 className="w-12 h-12" />
        </div>
        
        <span className="inline-block px-3 py-1 bg-emerald-100 text-emerald-800 text-xs font-semibold rounded-full uppercase tracking-wider mb-3">
          Order Placed Successfully
        </span>
        <h1 className="text-3xl font-bold text-slate-900 tracking-tight mb-2">
          Thank you for your purchase!
        </h1>
        <p className="text-slate-600 max-w-md mx-auto mb-6">
          We have received your order and our fulfillment team is preparing it for shipment.
        </p>

        {error && (
          <div className="p-4 bg-amber-50 text-amber-800 rounded-xl mb-6 text-sm">
            {error}
          </div>
        )}

        {order && (
          <div className="bg-slate-50 border border-slate-200/60 rounded-2xl p-6 text-left mb-8 space-y-4">
            <div className="flex flex-wrap items-center justify-between pb-4 border-b border-slate-200 gap-2">
              <div>
                <span className="text-xs text-slate-500 font-medium block">Order Number</span>
                <span className="font-mono text-base font-bold text-indigo-700">{order.orderNumber}</span>
              </div>
              <div>
                <span className="text-xs text-slate-500 font-medium block">Status</span>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                  {order.status}
                </span>
              </div>
              <div>
                <span className="text-xs text-slate-500 font-medium block">Total Amount</span>
                <span className="font-bold text-slate-900 text-base">₹{order.grandTotal.toLocaleString('en-IN')}</span>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-semibold text-slate-800 mb-3 flex items-center gap-2">
                <Package className="w-4 h-4 text-indigo-600" /> Items in this Order ({order.items?.length || 0})
              </h3>
              <div className="divide-y divide-slate-100 max-h-56 overflow-y-auto pr-2">
                {order.items?.map((item) => (
                  <div key={item.id} className="py-2.5 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <div className="w-12 h-12 rounded-lg bg-white border border-slate-200 flex-shrink-0 overflow-hidden flex items-center justify-center">
                        {item.productImage ? (
                          <img src={item.productImage} alt={item.productName} className="w-full h-full object-cover" />
                        ) : (
                          <Package className="w-6 h-6 text-slate-400" />
                        )}
                      </div>
                      <div>
                        <p className="text-sm font-medium text-slate-800 line-clamp-1">{item.productName}</p>
                        <p className="text-xs text-slate-500">Qty: {item.quantity} × ₹{item.unitPrice.toLocaleString('en-IN')}</p>
                      </div>
                    </div>
                    <span className="text-sm font-semibold text-slate-800">
                      ₹{(item.totalPrice || item.lineTotal || (item.unitPrice * item.quantity)).toLocaleString('en-IN')}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {order.shippingAddress && (
              <div className="pt-3 border-t border-slate-200 text-xs text-slate-600">
                <span className="font-semibold text-slate-700 block mb-1">Delivering to:</span>
                <p>{order.shippingAddress.fullName} - {order.shippingAddress.line1}, {order.shippingAddress.city}, {order.shippingAddress.state} - {order.shippingAddress.postalCode}</p>
              </div>
            )}

            <div className="pt-3 border-t border-slate-200 flex items-start gap-3 text-xs text-slate-600">
              <Truck className="w-4 h-4 text-slate-500 mt-0.5 flex-shrink-0" />
              <div>
                <span className="font-medium text-slate-700">Estimated Delivery: </span>
                <span>3 - 5 business days</span>
              </div>
            </div>
          </div>
        )}

        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/orders"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold shadow-sm transition"
          >
            <Package className="w-4 h-4" /> View My Orders
          </Link>
          <Link
            to="/products"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-3 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold transition"
          >
            <Home className="w-4 h-4" /> Continue Shopping <ArrowRight className="w-4 h-4 ml-1" />
          </Link>
        </div>
      </div>
    </div>
  );
};

export default OrderConfirmation;
