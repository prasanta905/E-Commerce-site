import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ShoppingBag,
  Trash2,
  ArrowRight,
  Tag,
  ShieldCheck,
  CheckCircle,
  AlertCircle,
} from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
import { orderService } from '../services/orderService';
import { CouponApplication } from '../types';
import LoadingSpinner from '../components/LoadingSpinner';

export default function Cart() {
  const navigate = useNavigate();
  const { cart, isLoading, fetchCart, updateQuantity, removeItem } = useCartStore();
  const { isAuthenticated } = useAuthStore();

  const [couponCode, setCouponCode] = useState('WELCOME10');
  const [appliedCoupon, setAppliedCoupon] = useState<CouponApplication | null>(null);
  const [couponLoading, setCouponLoading] = useState(false);
  const [couponError, setCouponError] = useState('');

  useEffect(() => {
    if (isAuthenticated) {
      fetchCart();
    }
  }, [isAuthenticated, fetchCart]);

  if (!isAuthenticated) {
    return (
      <div className="max-w-md mx-auto py-24 px-4 text-center space-y-4">
        <div className="w-16 h-16 rounded-2xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center mx-auto">
          <ShoppingBag size={28} />
        </div>
        <h2 className="text-xl font-bold text-white">Your Shopping Cart</h2>
        <p className="text-xs text-slate-400">Please sign in to view your saved cart items and complete checkout.</p>
        <Link
          to="/login?redirect=/cart"
          className="inline-block px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold shadow-md transition-colors"
        >
          Sign In to Shop
        </Link>
      </div>
    );
  }

  const items = cart?.items || [];
  const subtotal = cart?.subtotal || 0;
  const discount = appliedCoupon ? appliedCoupon.discountAmount : 0;
  const shipping = 0; // Free shipping
  const finalTotal = Math.max(0, subtotal - discount + shipping);

  const handleApplyCoupon = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!couponCode.trim()) return;
    try {
      setCouponLoading(true);
      setCouponError('');
      const res = await orderService.applyCoupon(couponCode.trim(), subtotal);
      setAppliedCoupon(res);
    } catch (err: any) {
      setAppliedCoupon(null);
      setCouponError(err.response?.data?.error?.message || 'Invalid or expired coupon code');
    } finally {
      setCouponLoading(false);
    }
  };

  const handleCheckout = () => {
    navigate('/checkout', {
      state: {
        couponCode: appliedCoupon?.code,
        discountAmount: discount,
      },
    });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white tracking-tight">Shopping Cart</h1>
        <p className="text-xs text-slate-400 mt-1">Review your items before proceeding to checkout</p>
      </div>

      {isLoading && !cart ? (
        <LoadingSpinner label="Loading your cart..." />
      ) : items.length === 0 ? (
        <div className="text-center py-20 p-8 rounded-2xl bg-slate-900 border border-slate-800 max-w-lg mx-auto space-y-4">
          <div className="w-16 h-16 rounded-2xl bg-slate-800 text-slate-400 flex items-center justify-center mx-auto">
            <ShoppingBag size={28} />
          </div>
          <h2 className="text-lg font-bold text-white">Your cart is empty</h2>
          <p className="text-xs text-slate-400">
            Looks like you haven't added anything to your cart yet. Discover trending deals today!
          </p>
          <Link
            to="/products"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold shadow-md transition-colors"
          >
            <span>Start Shopping</span>
            <ArrowRight size={14} />
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Items list (8 cols) */}
          <div className="lg:col-span-8 space-y-4">
            <div className="rounded-2xl bg-slate-900 border border-slate-800 divide-y divide-slate-800 overflow-hidden">
              {items.map((item: any) => (
                <div key={item.itemId} className="p-4 sm:p-5 flex gap-4 items-center">
                  {/* Thumbnail */}
                  <Link
                    to={`/products/${item.productId}`}
                    className="w-20 h-20 rounded-xl bg-slate-950 border border-slate-800 p-2 shrink-0 flex items-center justify-center overflow-hidden"
                  >
                    {item.productImageUrl ? (
                      <img src={item.productImageUrl} alt={item.productName} className="w-full h-full object-contain" />
                    ) : (
                      <ShoppingBag size={24} className="text-slate-600" />
                    )}
                  </Link>

                  {/* Info */}
                  <div className="flex-1 min-w-0 space-y-1">
                    <Link
                      to={`/products/${item.productId}`}
                      className="text-xs sm:text-sm font-semibold text-slate-100 hover:text-indigo-400 line-clamp-2 transition-colors"
                    >
                      {item.productName}
                    </Link>
                    <p className="text-xs font-bold text-white">
                      ₹{item.unitPrice?.toLocaleString('en-IN')}
                    </p>
                    <p className="text-[11px] text-emerald-400 font-medium">In Stock</p>
                  </div>

                  {/* Quantity & Actions */}
                  <div className="flex flex-col items-end gap-2 shrink-0">
                    <div className="flex items-center border border-slate-800 rounded-lg bg-slate-950 px-2 py-1">
                      <button
                        type="button"
                        onClick={() => updateQuantity(item.itemId, item.quantity - 1)}
                        className="px-1.5 text-xs text-slate-400 hover:text-white font-bold"
                      >
                        -
                      </button>
                      <span className="px-2.5 text-xs font-bold text-white min-w-[1.5rem] text-center">
                        {item.quantity}
                      </span>
                      <button
                        type="button"
                        onClick={() => updateQuantity(item.itemId, item.quantity + 1)}
                        className="px-1.5 text-xs text-slate-400 hover:text-white font-bold"
                      >
                        +
                      </button>
                    </div>

                    <button
                      type="button"
                      onClick={() => removeItem(item.itemId)}
                      className="text-[11px] text-slate-500 hover:text-rose-400 flex items-center gap-1 transition-colors"
                    >
                      <Trash2 size={12} /> Remove
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Confidence assurance */}
            <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-800/80 flex items-center gap-3 text-xs text-slate-400">
              <ShieldCheck size={20} className="text-emerald-400 shrink-0" />
              <span>Safe and secure payments. 100% authentic products guaranteed by ShopSphere.</span>
            </div>
          </div>

          {/* Order Summary & Coupon (4 cols) */}
          <div className="lg:col-span-4 space-y-4">
            {/* Coupon Card */}
            <div className="p-5 rounded-2xl bg-slate-900 border border-slate-800 space-y-3">
              <span className="text-xs font-bold text-slate-200 uppercase tracking-wider flex items-center gap-1.5">
                <Tag size={14} className="text-indigo-400" /> Apply Coupon
              </span>

              <form onSubmit={handleApplyCoupon} className="flex gap-2">
                <input
                  type="text"
                  placeholder="Enter coupon code"
                  value={couponCode}
                  onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
                  className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 uppercase tracking-wider focus:outline-none focus:border-indigo-500"
                />
                <button
                  type="submit"
                  disabled={couponLoading || !couponCode}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold disabled:opacity-50 transition-colors"
                >
                  {couponLoading ? '...' : 'Apply'}
                </button>
              </form>

              {appliedCoupon && (
                <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs flex items-center gap-2">
                  <CheckCircle size={14} className="shrink-0" />
                  <span>{appliedCoupon.message}</span>
                </div>
              )}

              {couponError && (
                <div className="p-2.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-center gap-2">
                  <AlertCircle size={14} className="shrink-0" />
                  <span>{couponError}</span>
                </div>
              )}
            </div>

            {/* Summary Card */}
            <div className="p-5 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-xl">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">Order Summary</h3>

              <div className="space-y-2.5 text-xs divide-y divide-slate-800">
                <div className="flex justify-between text-slate-300 pt-1">
                  <span>Subtotal ({items.length} items)</span>
                  <span className="font-semibold text-white">₹{subtotal.toLocaleString('en-IN')}</span>
                </div>

                {discount > 0 && (
                  <div className="flex justify-between text-emerald-400 pt-2.5 font-semibold">
                    <span>Coupon Discount</span>
                    <span>-₹{discount.toLocaleString('en-IN')}</span>
                  </div>
                )}

                <div className="flex justify-between text-slate-300 pt-2.5">
                  <span>Shipping Fee</span>
                  <span className="font-semibold text-emerald-400">FREE</span>
                </div>

                <div className="flex justify-between text-sm font-extrabold text-white pt-3">
                  <span>Final Total</span>
                  <span className="text-indigo-400 text-base">₹{finalTotal.toLocaleString('en-IN')}</span>
                </div>
              </div>

              <button
                type="button"
                onClick={handleCheckout}
                className="w-full py-3.5 px-4 bg-indigo-600 hover:bg-indigo-500 active:scale-98 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 transition-all"
              >
                <span>Proceed to Checkout</span>
                <ArrowRight size={15} />
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
