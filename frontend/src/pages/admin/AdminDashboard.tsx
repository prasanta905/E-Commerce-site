import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  TrendingUp,
  DollarSign,
  ShoppingBag,
  Users,
  AlertTriangle,
  RotateCcw,
  Package,
  ArrowUpRight,
  RefreshCw,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { adminService } from '../../services/adminService';
import type { AdminDashboard as DashboardData, AdminAnalytics } from '../../types';
import LoadingSpinner from '../../components/LoadingSpinner';

const AdminDashboard: React.FC = () => {
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [analytics, setAnalytics] = useState<AdminAnalytics | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [period, setPeriod] = useState<string>('daily');
  const [refreshing, setRefreshing] = useState<boolean>(false);

  const loadData = async () => {
    try {
      const [dashData, analyticsData] = await Promise.all([
        adminService.getDashboard(),
        adminService.getAnalytics(period),
      ]);
      setDashboard(dashData);
      setAnalytics(analyticsData);
    } catch (err) {
      console.error('Failed to load dashboard data', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [period]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadData();
  };

  if (loading && !dashboard) {
    return <LoadingSpinner fullPage text="Loading management metrics..." />;
  }

  const chartData = analytics?.points?.map((pt) => ({
    name: pt.bucket.length > 10 ? pt.bucket.substring(5) : pt.bucket,
    revenue: pt.revenue,
    orders: pt.orders,
  })) || [];

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            Executive Overview
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Real-time sales, order fulfillment, and platform inventory monitoring
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex bg-slate-100 p-1 rounded-xl text-xs font-semibold">
            <button
              onClick={() => setPeriod('daily')}
              className={`px-3 py-1.5 rounded-lg transition ${
                period === 'daily' ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Daily
            </button>
            <button
              onClick={() => setPeriod('weekly')}
              className={`px-3 py-1.5 rounded-lg transition ${
                period === 'weekly' ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Weekly
            </button>
            <button
              onClick={() => setPeriod('monthly')}
              className={`px-3 py-1.5 rounded-lg transition ${
                period === 'monthly' ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Monthly
            </button>
          </div>

          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="p-2 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 transition"
            title="Refresh Data"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* Card 1: Revenue Total */}
        <div className="bg-white rounded-3xl p-6 border border-slate-200/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between mb-4">
            <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
              Total Revenue
            </span>
            <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <DollarSign className="w-5 h-5" />
            </div>
          </div>
          <div className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            ₹{dashboard?.revenueTotal?.toLocaleString('en-IN') || 0}
          </div>
          <div className="flex items-center gap-1.5 text-xs text-emerald-600 font-semibold mt-2">
            <TrendingUp className="w-3.5 h-3.5" />
            <span>₹{dashboard?.revenueMonth?.toLocaleString('en-IN') || 0} this month</span>
          </div>
        </div>

        {/* Card 2: Orders Today */}
        <div className="bg-white rounded-3xl p-6 border border-slate-200/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between mb-4">
            <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
              Orders Today
            </span>
            <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <ShoppingBag className="w-5 h-5" />
            </div>
          </div>
          <div className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {dashboard?.ordersToday ?? 0}
          </div>
          <div className="flex items-center gap-1.5 text-xs text-slate-500 mt-2">
            <span>Today's sales: ₹{dashboard?.revenueToday?.toLocaleString('en-IN') || 0}</span>
          </div>
        </div>

        {/* Card 3: Customers */}
        <div className="bg-white rounded-3xl p-6 border border-slate-200/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between mb-4">
            <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
              Customers
            </span>
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Users className="w-5 h-5" />
            </div>
          </div>
          <div className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {dashboard?.totalCustomers ?? 0}
          </div>
          <div className="flex items-center gap-1.5 text-xs text-indigo-600 font-semibold mt-2">
            <ArrowUpRight className="w-3.5 h-3.5" />
            <span>+{dashboard?.newCustomersToday ?? 0} joined today</span>
          </div>
        </div>

        {/* Card 4: Inventory Alerts */}
        <div className="bg-white rounded-3xl p-6 border border-slate-200/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between mb-4">
            <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
              Low Stock Alerts
            </span>
            <div className="w-10 h-10 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center">
              <AlertTriangle className="w-5 h-5" />
            </div>
          </div>
          <div className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {dashboard?.lowStockCount ?? 0}
          </div>
          <div className="flex items-center gap-1 text-xs text-slate-500 mt-2">
            <Link to="/admin/inventory" className="text-amber-600 hover:underline font-semibold flex items-center gap-1">
              Restock needed <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>
        </div>
      </div>

      {/* Revenue Performance Area Chart */}
      <div className="bg-white rounded-3xl p-6 sm:p-8 border border-slate-200/80 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Revenue & Demand Velocity</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Historical sales trends over selected {period} periods
            </p>
          </div>
          {analytics && (
            <div className="flex items-center gap-6 text-xs">
              <div>
                <span className="text-slate-400 block font-medium">Avg Order Value</span>
                <span className="font-bold text-slate-800 text-sm">
                  ₹{Math.round(analytics.averageOrderValue || 0).toLocaleString('en-IN')}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block font-medium">Total Volume</span>
                <span className="font-bold text-indigo-600 text-sm">
                  ₹{Math.round(analytics.totalRevenue || 0).toLocaleString('en-IN')}
                </span>
              </div>
            </div>
          )}
        </div>

        <div className="h-72 w-full">
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorRevenue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#4f46e5" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#4f46e5" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis
                  dataKey="name"
                  stroke="#94a3b8"
                  fontSize={11}
                  tickLine={false}
                  axisLine={{ stroke: '#e2e8f0' }}
                />
                <YAxis
                  stroke="#94a3b8"
                  fontSize={11}
                  tickLine={false}
                  axisLine={false}
                  tickFormatter={(val) => `₹${val}`}
                />
                <Tooltip
                  formatter={(value: any) => [`₹${Number(value).toLocaleString('en-IN')}`, 'Revenue']}
                  contentStyle={{
                    backgroundColor: '#ffffff',
                    borderRadius: '1rem',
                    border: '1px solid #e2e8f0',
                    boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="revenue"
                  stroke="#4f46e5"
                  strokeWidth={2.5}
                  fillOpacity={1}
                  fill="url(#colorRevenue)"
                />
              </AreaChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-full flex items-center justify-center text-slate-400 text-sm">
              No sales activity recorded in this period yet.
            </div>
          )}
        </div>
      </div>

      {/* Orders Breakdown & Quick Action Panels */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Status Distribution */}
        <div className="lg:col-span-2 bg-white rounded-3xl p-6 sm:p-8 border border-slate-200/80 shadow-sm">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-base font-bold text-slate-900">Order Fulfillment Distribution</h3>
            <Link to="/admin/orders" className="text-xs font-semibold text-indigo-600 hover:underline">
              Manage Orders &rarr;
            </Link>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {dashboard?.ordersByStatus &&
              Object.entries(dashboard.ordersByStatus).map(([status, count]) => (
                <div
                  key={status}
                  className="bg-slate-50 border border-slate-200/60 rounded-2xl p-4 text-center hover:bg-slate-100/60 transition"
                >
                  <span className="text-2xl font-black text-slate-900 block">{count}</span>
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 mt-1 block">
                    {status.replace(/_/g, ' ')}
                  </span>
                </div>
              ))}
          </div>
        </div>

        {/* Action Highlights */}
        <div className="bg-white rounded-3xl p-6 sm:p-8 border border-slate-200/80 shadow-sm space-y-4">
          <h3 className="text-base font-bold text-slate-900 mb-2">Attention Required</h3>

          <Link
            to="/admin/inventory"
            className="flex items-center justify-between p-4 rounded-2xl bg-amber-50/70 border border-amber-200/70 hover:bg-amber-100/70 transition group"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-100 text-amber-700 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-800">
                  {dashboard?.lowStockCount ?? 0} Low Stock SKUs
                </p>
                <p className="text-xs text-slate-500">Inventory threshold reached</p>
              </div>
            </div>
            <ArrowUpRight className="w-4 h-4 text-amber-700 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition" />
          </Link>

          <Link
            to="/admin/returns"
            className="flex items-center justify-between p-4 rounded-2xl bg-purple-50/70 border border-purple-200/70 hover:bg-purple-100/70 transition group"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-purple-100 text-purple-700 flex items-center justify-center">
                <RotateCcw className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-800">
                  {dashboard?.pendingReturns ?? 0} Return Requests
                </p>
                <p className="text-xs text-slate-500">Awaiting approval / refund</p>
              </div>
            </div>
            <ArrowUpRight className="w-4 h-4 text-purple-700 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition" />
          </Link>

          <Link
            to="/admin/products"
            className="flex items-center justify-between p-4 rounded-2xl bg-indigo-50/70 border border-indigo-200/70 hover:bg-indigo-100/70 transition group"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-100 text-indigo-700 flex items-center justify-center">
                <Package className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-800">
                  {dashboard?.activeProducts ?? 0} Live Catalog Items
                </p>
                <p className="text-xs text-slate-500">Published and searchable</p>
              </div>
            </div>
            <ArrowUpRight className="w-4 h-4 text-indigo-700 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition" />
          </Link>
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
