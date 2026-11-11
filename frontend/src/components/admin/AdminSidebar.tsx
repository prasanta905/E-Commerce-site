import { Link, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  ShoppingBag,
  Layers,
  Tag,
  Warehouse,
  Users,
  Ticket,
  RotateCcw,
  ArrowLeft,
  ShieldCheck,
} from 'lucide-react';

export default function AdminSidebar() {
  const location = useLocation();

  const links = [
    { label: 'Overview', to: '/admin', icon: LayoutDashboard },
    { label: 'Products', to: '/admin/products', icon: Package },
    { label: 'Orders', to: '/admin/orders', icon: ShoppingBag },
    { label: 'Inventory', to: '/admin/inventory', icon: Warehouse },
    { label: 'Categories', to: '/admin/categories', icon: Layers },
    { label: 'Brands', to: '/admin/brands', icon: Tag },
    { label: 'Customers', to: '/admin/customers', icon: Users },
    { label: 'Coupons', to: '/admin/coupons', icon: Ticket },
    { label: 'Returns', to: '/admin/returns', icon: RotateCcw },
  ];

  return (
    <aside className="w-64 bg-slate-900 border-r border-slate-800 flex flex-col shrink-0 min-h-screen">
      {/* Brand */}
      <div className="p-5 border-b border-slate-800 flex items-center justify-between">
        <Link to="/admin" className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center text-white shadow-md">
            <ShieldCheck size={20} />
          </div>
          <div>
            <h1 className="text-sm font-bold text-white tracking-tight">Admin Portal</h1>
            <p className="text-[10px] text-indigo-400 font-semibold tracking-wider uppercase">ShopSphere v1.0</p>
          </div>
        </Link>
      </div>

      {/* Nav Menu */}
      <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
        <p className="px-3 py-2 text-[11px] font-bold uppercase tracking-wider text-slate-500">
          Management
        </p>
        {links.map((link) => {
          const Icon = link.icon;
          const isActive =
            link.to === '/admin'
              ? location.pathname === '/admin'
              : location.pathname.startsWith(link.to);

          return (
            <Link
              key={link.to}
              to={link.to}
              className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                isActive
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/25'
                  : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/60'
              }`}
            >
              <Icon size={17} />
              <span>{link.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Footer Back to Store */}
      <div className="p-4 border-t border-slate-800">
        <Link
          to="/"
          className="flex items-center justify-center gap-2 w-full py-2.5 px-3 bg-slate-800 hover:bg-slate-700/80 text-slate-200 rounded-xl text-xs font-semibold transition-colors"
        >
          <ArrowLeft size={15} />
          <span>Back to Storefront</span>
        </Link>
      </div>
    </aside>
  );
}
