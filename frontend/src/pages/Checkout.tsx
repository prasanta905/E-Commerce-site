import { useState, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import {
  MapPin,
  CreditCard,
  Banknote,
  ShieldCheck,
  Plus,
  CheckCircle,
  AlertCircle,
  ArrowRight,
  ShoppingBag,
  Truck,
} from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
import { authService } from '../services/authService';
import { orderService } from '../services/orderService';
import { paymentService } from '../services/paymentService';
import { Address, CheckoutPreview } from '../types';
import LoadingSpinner from '../components/LoadingSpinner';

export default function Checkout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { cart, fetchCart, clearCart } = useCartStore();
  const { isAuthenticated } = useAuthStore();

  const couponCode = (location.state as any)?.couponCode || undefined;
  const passedDiscount = (location.state as any)?.discountAmount || 0;

  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'RAZORPAY'>('COD');
  const [shippingMethods, setShippingMethods] = useState<Array<{ id: number; code: string; name: string; baseFee: number; freeAbove: number }>>([]);
  const [selectedShippingId, setSelectedShippingId] = useState<number | null>(null);
  const [preview, setPreview] = useState<CheckoutPreview | null>(null);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  // Add Address Modal state
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [newContactName, setNewContactName] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newLine1, setNewLine1] = useState('');
  const [newCity, setNewCity] = useState('');
  const [newState, setNewState] = useState('');
  const [newPincode, setNewPincode] = useState('');

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?redirect=/checkout');
      return;
    }

    const init = async () => {
      try {
        setIsLoading(true);
        const [_, addrList, methods] = await Promise.all([
          fetchCart(),
          authService.getAddresses(),
          orderService.getShippingMethods(),
        ]);
        setAddresses(addrList || []);
        if (addrList && addrList.length > 0) {
          const defaultAddr = addrList.find((a: Address) => a.isDefault) || addrList[0];
          setSelectedAddressId(defaultAddr.id);
        }
        setShippingMethods(methods || []);
        if (methods && methods.length > 0) {
          setSelectedShippingId(methods[0].id);
        }
      } catch (err) {
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };
    init();
  }, [isAuthenticated, fetchCart, navigate]);

  // Server-computed quote (prices/discounts/shipping are never trusted client-side)
  useEffect(() => {
    if (!selectedAddressId || !selectedShippingId) return;
    let cancelled = false;
    orderService
      .previewCheckout({ addressId: selectedAddressId, shippingMethodId: selectedShippingId, couponCode })
      .then((data) => {
        if (!cancelled) setPreview(data);
      })
      .catch((err) => {
        if (!cancelled) {
          console.error('Preview failed', err);
          setPreview(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [selectedAddressId, selectedShippingId, couponCode]);

  const subtotal = preview?.subtotal ?? cart?.subtotal ?? 0;
  const discount = preview?.discount ?? passedDiscount;
  const shippingFee = preview?.shippingFee ?? 0;
  const total = Math.max(0, subtotal - discount + shippingFee);

  const handleAddNewAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const added = await authService.addAddress({
        contactName: newContactName,
        phone: newPhone,
        line1: newLine1,
        city: newCity,
        state: newState,
        pincode: newPincode,
        isDefault: addresses.length === 0,
      });
      const updated = await authService.getAddresses();
      setAddresses(updated);
      setSelectedAddressId(added.id);
      setShowAddressForm(false);
    } catch (err) {
      console.error('Failed to add address', err);
    }
  };

  const handlePlaceOrder = async () => {
    if (!selectedAddressId) {
      setErrorMsg('Please select or add a delivery address.');
      return;
    }
    if (!selectedShippingId) {
      setErrorMsg('Please choose a delivery speed.');
      return;
    }
    if (!agreeTerms) {
      setErrorMsg('Please accept the terms of service to place your order.');
      return;
    }
    try {
      setIsSubmitting(true);
      setErrorMsg('');

      const idempotencyKey = `ss-order-${Date.now()}-${Math.random().toString(36).substring(7)}`;

      const res = await orderService.placeOrder({
        addressId: selectedAddressId,
        shippingMethodId: selectedShippingId,
        couponCode: couponCode || undefined,
        paymentMethod,
        idempotencyKey,
        termsAccepted: 'true',
      });

      if (paymentMethod === 'COD') {
        // Clear local cart and go straight to confirmation
        await clearCart();
        navigate(`/orders/${res.orderId}/confirmed`, {
          state: { orderId: res.orderId, paymentMethod },
        });
        return;
      }

      // ---- Razorpay flow: resolve gateway order → checkout modal → verify ----
      let setup;
      try {
        setup = await paymentService.createRazorpayOrder(res.orderId);
      } catch (setupErr: any) {
        const code = setupErr.response?.data?.error?.code;
        setErrorMsg(
          code === 'RAZORPAY_NOT_CONFIGURED'
            ? 'Online payment is not configured on this environment. Please use Cash on Delivery.'
            : setupErr.response?.data?.message || 'Could not start online payment. You can complete payment from your order page.'
        );
        setIsSubmitting(false);
        return;
      }

      const scriptLoaded = await paymentService.loadCheckoutScript();
      const RazorpayCtor = (window as unknown as {
        Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
      }).Razorpay;
      if (!scriptLoaded || !RazorpayCtor || !setup.keyId) {
        setErrorMsg('Payment gateway could not be loaded. Check your connection or use Cash on Delivery.');
        setIsSubmitting(false);
        return;
      }

      const checkout = new RazorpayCtor({
        key: setup.keyId,
        amount: Math.round(setup.amount * 100), // paise
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
            await clearCart();
            navigate(`/orders/${res.orderId}/confirmed`, {
              state: { orderId: res.orderId, paymentMethod },
            });
          } catch (verifyErr: any) {
            setErrorMsg(
              verifyErr.response?.data?.message ||
                'Payment verification failed. Contact support with your order number.'
            );
          } finally {
            setIsSubmitting(false);
          }
        },
        modal: {
          ondismiss: () => {
            setErrorMsg(
              'Payment window closed before completion. Your order is saved as Pending — you can pay for it from your orders page.'
            );
            setIsSubmitting(false);
          },
        },
      });
      checkout.open();
    } catch (err: any) {
      setErrorMsg(err.response?.data?.error?.message || 'Failed to place order. Please check stock or address.');
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <LoadingSpinner label="Preparing your checkout session..." />;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="max-w-md mx-auto py-24 text-center space-y-4">
        <h2 className="text-xl font-bold text-white">Your Cart is Empty</h2>
        <p className="text-xs text-slate-400">Add products to your cart before proceeding to checkout.</p>
        <Link to="/products" className="inline-block px-5 py-2.5 bg-indigo-600 text-white rounded-xl text-xs font-semibold">
          Explore Catalogue
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white tracking-tight">Checkout</h1>
        <p className="text-xs text-slate-400 mt-1">Select your delivery address and preferred payment method</p>
      </div>

      {errorMsg && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-center gap-2">
          <AlertCircle size={16} className="shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Options (8 cols) */}
        <div className="lg:col-span-8 space-y-6">
          {/* Step 1: Delivery Address */}
          <section className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h2 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider">
                <MapPin size={16} className="text-indigo-400" />
                1. Delivery Address
              </h2>
              <button
                type="button"
                onClick={() => setShowAddressForm(!showAddressForm)}
                className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
              >
                <Plus size={14} /> Add New Address
              </button>
            </div>

            {/* Address Form (if opened) */}
            {showAddressForm && (
              <form onSubmit={handleAddNewAddress} className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-3">
                <h4 className="text-xs font-bold text-white">Add Delivery Address</h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <input
                    type="text"
                    required
                    placeholder="Full Name"
                    value={newContactName}
                    onChange={(e) => setNewContactName(e.target.value)}
                    className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                  <input
                    type="tel"
                    required
                    placeholder="10-digit Mobile Number"
                    value={newPhone}
                    onChange={(e) => setNewPhone(e.target.value)}
                    className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                  <input
                    type="text"
                    required
                    placeholder="Flat, House no., Building, Apartment"
                    value={newLine1}
                    onChange={(e) => setNewLine1(e.target.value)}
                    className="sm:col-span-2 bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                  <input
                    type="text"
                    required
                    placeholder="City"
                    value={newCity}
                    onChange={(e) => setNewCity(e.target.value)}
                    className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                  <input
                    type="text"
                    required
                    placeholder="State"
                    value={newState}
                    onChange={(e) => setNewState(e.target.value)}
                    className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                  <input
                    type="text"
                    required
                    placeholder="6-digit PIN Code"
                    value={newPincode}
                    onChange={(e) => setNewPincode(e.target.value)}
                    className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div className="flex gap-2 pt-1">
                  <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-xs font-semibold">
                    Save Address
                  </button>
                  <button type="button" onClick={() => setShowAddressForm(false)} className="px-3 py-2 bg-slate-800 text-slate-300 rounded-lg text-xs">
                    Cancel
                  </button>
                </div>
              </form>
            )}

            {/* Address Selector list */}
            {addresses.length === 0 ? (
              <p className="text-xs text-slate-400 italic">No saved addresses. Please click "Add New Address" above.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {addresses.map((addr) => (
                  <label
                    key={addr.id}
                    className={`p-4 rounded-xl border cursor-pointer transition-all flex flex-col justify-between ${
                      selectedAddressId === addr.id
                        ? 'bg-indigo-950/20 border-indigo-500 ring-1 ring-indigo-500/50'
                        : 'bg-slate-950 border-slate-800/80 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-start gap-2.5">
                      <input
                        type="radio"
                        name="address"
                        checked={selectedAddressId === addr.id}
                        onChange={() => setSelectedAddressId(addr.id)}
                        className="mt-1 text-indigo-600 focus:ring-0"
                      />
                      <div className="text-xs space-y-1">
                        <p className="font-bold text-white">{addr.contactName}</p>
                        <p className="text-slate-300">{addr.line1}</p>
                        {addr.line2 && <p className="text-slate-300">{addr.line2}</p>}
                        <p className="text-slate-400">{addr.city}, {addr.state} - {addr.pincode}</p>
                        <p className="text-[11px] text-slate-500">Phone: {addr.phone}</p>
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            )}
          </section>

          {/* Step 2: Delivery Speed */}
          <section className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
            <h2 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider pb-3 border-b border-slate-800">
              <Truck size={16} className="text-indigo-400" />
              2. Delivery Speed
            </h2>
            {shippingMethods.length === 0 ? (
              <p className="text-xs text-slate-400">No shipping methods available right now.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {shippingMethods.map((m) => (
                  <label
                    key={m.id}
                    className={`p-4 rounded-xl border cursor-pointer transition-all flex items-start gap-3 ${
                      selectedShippingId === m.id
                        ? 'bg-indigo-950/20 border-indigo-500 ring-1 ring-indigo-500/50'
                        : 'bg-slate-950 border-slate-800/80 hover:border-slate-700'
                    }`}
                  >
                    <input
                      type="radio"
                      name="shippingMethod"
                      checked={selectedShippingId === m.id}
                      onChange={() => setSelectedShippingId(m.id)}
                      className="mt-1 text-indigo-600 focus:ring-0"
                    />
                    <div className="text-xs space-y-1">
                      <div className="flex items-center justify-between gap-2 font-bold text-white">
                        <span>{m.name}</span>
                        <span className={m.baseFee === 0 ? 'text-emerald-400' : ''}>
                          {m.baseFee === 0 ? 'FREE' : `₹${m.baseFee}`}
                        </span>
                      </div>
                      <p className="text-slate-400 text-[11px]">
                        {m.baseFee > 0 && `Free for orders above ₹${m.freeAbove.toLocaleString('en-IN')}`}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
            )}
          </section>

          {/* Step 3: Payment Method */}
          <section className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
            <h2 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider pb-3 border-b border-slate-800">
              <CreditCard size={16} className="text-indigo-400" />
              3. Payment Method
            </h2>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* COD */}
              <label
                className={`p-4 rounded-xl border cursor-pointer transition-all flex items-start gap-3 ${
                  paymentMethod === 'COD'
                    ? 'bg-indigo-950/20 border-indigo-500 ring-1 ring-indigo-500/50'
                    : 'bg-slate-950 border-slate-800/80 hover:border-slate-700'
                }`}
              >
                <input
                  type="radio"
                  name="paymentMethod"
                  value="COD"
                  checked={paymentMethod === 'COD'}
                  onChange={() => setPaymentMethod('COD')}
                  className="mt-1 text-indigo-600 focus:ring-0"
                />
                <div className="text-xs space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-white">
                    <Banknote size={16} className="text-emerald-400" />
                    <span>Cash on Delivery (COD)</span>
                  </div>
                  <p className="text-slate-400 text-[11px]">Pay in cash or UPI upon courier arrival at your door.</p>
                </div>
              </label>

              {/* Online payment */}
              <label
                className={`p-4 rounded-xl border cursor-pointer transition-all flex items-start gap-3 ${
                  paymentMethod === 'RAZORPAY'
                    ? 'bg-indigo-950/20 border-indigo-500 ring-1 ring-indigo-500/50'
                    : 'bg-slate-950 border-slate-800/80 hover:border-slate-700'
                }`}
              >
                <input
                  type="radio"
                  name="paymentMethod"
                  value="RAZORPAY"
                  checked={paymentMethod === 'RAZORPAY'}
                  onChange={() => setPaymentMethod('RAZORPAY')}
                  className="mt-1 text-indigo-600 focus:ring-0"
                />
                <div className="text-xs space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-white">
                    <CreditCard size={16} className="text-indigo-400" />
                    <span>Online Payment (Razorpay Test)</span>
                  </div>
                  <p className="text-slate-400 text-[11px]">Supports UPI, Net Banking, and Cards with instant confirmation.</p>
                </div>
              </label>
            </div>
          </section>
        </div>

        {/* Right Summary Area (4 cols) */}
        <div className="lg:col-span-4 space-y-4">
          <div className="p-5 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-xl">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider">Review Order</h3>

            {/* Items list snippet */}
            <div className="max-h-52 overflow-y-auto divide-y divide-slate-800/80 pr-1 text-xs">
              {cart.items.map((item) => (
                <div key={item.itemId} className="py-2.5 flex justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-slate-200 truncate">{item.productName}</p>
                    <p className="text-[11px] text-slate-400">Qty: {item.quantity} × ₹{item.unitPrice?.toLocaleString('en-IN')}</p>
                  </div>
                  <span className="font-bold text-white shrink-0">
                    ₹{item.lineTotal?.toLocaleString('en-IN')}
                  </span>
                </div>
              ))}
            </div>

            {/* Calculations */}
            <div className="space-y-2 text-xs pt-3 border-t border-slate-800">
              <div className="flex justify-between text-slate-300">
                <span>Subtotal</span>
                <span className="font-semibold text-white">₹{subtotal.toLocaleString('en-IN')}</span>
              </div>

              {discount > 0 && (
                <div className="flex justify-between text-emerald-400 font-semibold">
                  <span>Coupon ({couponCode})</span>
                  <span>-₹{discount.toLocaleString('en-IN')}</span>
                </div>
              )}

              <div className="flex justify-between text-slate-300">
                <span>Shipping {preview?.shippingMethodName ? `(${preview.shippingMethodName})` : ''}</span>
                <span className={shippingFee === 0 ? 'font-semibold text-emerald-400' : 'font-semibold text-white'}>
                  {shippingFee === 0 ? 'FREE' : `₹${shippingFee.toLocaleString('en-IN')}`}
                </span>
              </div>

              {preview?.stockIssues && preview.stockIssues.length > 0 && (
                <div className="flex items-start gap-2 p-2 rounded-lg bg-amber-950/40 text-amber-300">
                  <AlertCircle size={14} className="mt-0.5 shrink-0" />
                  <span>{preview.stockIssues.join(' ')}</span>
                </div>
              )}

              <div className="flex justify-between text-sm font-extrabold text-white pt-2 border-t border-slate-800">
                <span>Payable Amount</span>
                <span className="text-indigo-400 text-base">₹{total.toLocaleString('en-IN')}</span>
              </div>
            </div>

            {/* Terms acceptance (required by the backend to place an order) */}
            <label className="flex items-start gap-2.5 text-[11px] text-slate-400 cursor-pointer">
              <input
                type="checkbox"
                checked={agreeTerms}
                onChange={(e) => setAgreeTerms(e.target.checked)}
                className="mt-0.5 text-indigo-600 focus:ring-0"
              />
              <span>
                I agree to the <span className="text-indigo-400 underline">Terms of Service</span> and{' '}
                <span className="text-indigo-400 underline">Return Policy</span>.
              </span>
            </label>

            <button
              type="button"
              onClick={handlePlaceOrder}
              disabled={isSubmitting || !selectedAddressId || !agreeTerms}
              className="w-full py-3.5 px-4 bg-indigo-600 hover:bg-indigo-500 active:scale-98 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 disabled:opacity-50 transition-all"
            >
              {isSubmitting ? (
                <>
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>Securing Order...</span>
                </>
              ) : (
                <>
                  <span>Place Order • ₹{total.toLocaleString('en-IN')}</span>
                  <ArrowRight size={15} />
                </>
              )}
            </button>
          </div>

          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 flex items-center gap-2.5 text-xs text-slate-400">
            <ShieldCheck size={18} className="text-emerald-400 shrink-0" />
            <span>End-to-end encrypted transaction protected with industry-standard security.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
