import { Link } from 'react-router-dom';
import { ShoppingBag, ShieldCheck, Truck, RotateCcw, Headphones, Heart } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="bg-slate-950 border-t border-slate-800 text-slate-400 text-xs">
      {/* Value props banner */}
      <div className="border-b border-slate-800/80 bg-slate-900/50 py-8">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 grid grid-cols-2 md:grid-cols-4 gap-6">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center shrink-0">
              <Truck size={20} />
            </div>
            <div>
              <p className="font-semibold text-slate-200">Express Delivery</p>
              <p className="text-[11px] text-slate-400">Fast shipping across all pincodes</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center shrink-0">
              <ShieldCheck size={20} />
            </div>
            <div>
              <p className="font-semibold text-slate-200">100% Genuine</p>
              <p className="text-[11px] text-slate-400">Authentic products with warranty</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center shrink-0">
              <RotateCcw size={20} />
            </div>
            <div>
              <p className="font-semibold text-slate-200">Easy 7-Day Returns</p>
              <p className="text-[11px] text-slate-400">Hassle-free replacement & refunds</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center shrink-0">
              <Headphones size={20} />
            </div>
            <div>
              <p className="font-semibold text-slate-200">24/7 Priority Support</p>
              <p className="text-[11px] text-slate-400">Dedicated customer happiness team</p>
            </div>
          </div>
        </div>
      </div>

      {/* Main footer content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-5 gap-8">
          {/* Brand info */}
          <div className="md:col-span-2 space-y-3">
            <Link to="/" className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center text-white">
                <ShoppingBag size={18} />
              </div>
              <span className="text-lg font-bold text-white tracking-tight">
                Shop<span className="text-indigo-400">Sphere</span>
              </span>
            </Link>
            <p className="text-slate-400 text-xs leading-relaxed max-w-sm">
              ShopSphere is an enterprise-grade full-stack commerce destination engineered for speed,
              reliability, and exceptional product discovery.
            </p>
            <div className="pt-2 flex items-center gap-2 text-[11px] text-slate-400">
              <span>Crafted with</span>
              <Heart size={13} className="text-rose-500 fill-rose-500" />
              <span>Spring Boot 3.5 & React 19</span>
            </div>
          </div>

          {/* Catalog links */}
          <div>
            <h4 className="text-xs font-semibold text-slate-200 uppercase tracking-wider mb-3">Shop Categories</h4>
            <ul className="space-y-2">
              <li><Link to="/products?categoryId=2" className="hover:text-indigo-400 transition-colors">Smartphones</Link></li>
              <li><Link to="/products?categoryId=3" className="hover:text-indigo-400 transition-colors">Laptops & PC</Link></li>
              <li><Link to="/products?categoryId=4" className="hover:text-indigo-400 transition-colors">Audio & Sound</Link></li>
              <li><Link to="/products?categoryId=5" className="hover:text-indigo-400 transition-colors">Fashion Collection</Link></li>
              <li><Link to="/products?categoryId=8" className="hover:text-indigo-400 transition-colors">Home & Kitchen</Link></li>
            </ul>
          </div>

          {/* Customer Care */}
          <div>
            <h4 className="text-xs font-semibold text-slate-200 uppercase tracking-wider mb-3">Customer Care</h4>
            <ul className="space-y-2">
              <li><Link to="/orders" className="hover:text-indigo-400 transition-colors">Track Orders</Link></li>
              <li><Link to="/profile" className="hover:text-indigo-400 transition-colors">Account Settings</Link></li>
              <li><Link to="/cart" className="hover:text-indigo-400 transition-colors">Shopping Cart</Link></li>
              <li><Link to="/wishlist" className="hover:text-indigo-400 transition-colors">Wishlist</Link></li>
            </ul>
          </div>

          {/* Quick Demo Credentials */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-3.5 space-y-1.5">
            <h4 className="text-xs font-semibold text-indigo-400 uppercase tracking-wider">Demo Credentials</h4>
            <div className="text-[11px] text-slate-300">
              <p><strong className="text-white">Admin:</strong> admin@shopsphere.local / <code className="text-indigo-300">Admin@123</code></p>
              <p className="mt-1"><strong className="text-white">Customer:</strong> customer1@shopsphere.local / <code className="text-indigo-300">User@123</code></p>
            </div>
            <p className="text-[10px] text-slate-400 mt-2">Use demo buttons on the Sign In page for one-click access.</p>
          </div>
        </div>

        {/* Bottom copyright */}
        <div className="mt-10 pt-6 border-t border-slate-800/80 flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-slate-400">
          <p>© 2026 ShopSphere Inc. All rights reserved.</p>
          <div className="flex items-center gap-4">
            <a href="/swagger-ui.html" target="_blank" rel="noreferrer" className="hover:text-indigo-400 transition-colors">
              Swagger OpenAPI Docs
            </a>
            <a href="/actuator/health" target="_blank" rel="noreferrer" className="hover:text-indigo-400 transition-colors">
              System Health
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
