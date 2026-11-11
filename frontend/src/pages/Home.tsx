import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Sparkles,
  Zap,
  TrendingUp,
  ShieldCheck,
  Star,
  Quote,
  CheckCircle,
} from 'lucide-react';
import { productService } from '../services/productService';
import { ProductCard as ProductCardType, Category } from '../types';
import ProductCard from '../components/ProductCard';
import LoadingSpinner from '../components/LoadingSpinner';

export default function Home() {
  const [featuredProducts, setFeaturedProducts] = useState<ProductCardType[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadHomeData = async () => {
      try {
        setIsLoading(true);
        const [featured, cats] = await Promise.all([
          productService.getFeatured(0, 8),
          productService.getCategories(),
        ]);
        setFeaturedProducts(featured || []);
        setCategories(cats || []);
      } catch (err) {
        console.error('Failed to load home data', err);
      } finally {
        setIsLoading(false);
      }
    };
    loadHomeData();
  }, []);

  return (
    <div className="space-y-16 pb-16">
      {/* Hero Section */}
      <section className="relative overflow-hidden bg-gradient-to-b from-slate-900 via-slate-950 to-slate-950 pt-12 pb-20 border-b border-slate-800">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_80%_80%_at_50%_-20%,rgba(99,102,241,0.25),rgba(255,255,255,0))]" />

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
            {/* Left copy */}
            <div className="lg:col-span-7 space-y-6 text-center lg:text-left">
              <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-semibold">
                <Sparkles size={13} className="text-indigo-400" />
                <span>Next-Generation Online Shopping</span>
              </div>

              <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-white tracking-tight leading-[1.1]">
                Curated Electronics & Modern Lifestyle
                <span className="block bg-gradient-to-r from-indigo-400 via-purple-300 to-pink-400 bg-clip-text text-transparent">
                  Delivered With Speed.
                </span>
              </h1>

              <p className="text-slate-400 text-sm sm:text-base leading-relaxed max-w-xl mx-auto lg:mx-0">
                Discover flagship smartphones, ultrabooks, studio audio, designer fashion, and smart home tech. Verified quality with guaranteed authentic warranty.
              </p>

              <div className="flex flex-wrap items-center justify-center lg:justify-start gap-3 pt-2">
                <Link
                  to="/products"
                  className="px-6 py-3.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-bold flex items-center gap-2 shadow-lg shadow-indigo-600/30 transition-all hover:gap-3"
                >
                  <span>Explore Catalogue</span>
                  <ArrowRight size={16} />
                </Link>
                <Link
                  to="/products?sort=popularity"
                  className="px-6 py-3.5 bg-slate-800/80 hover:bg-slate-800 text-slate-200 border border-slate-700/80 rounded-xl text-sm font-semibold transition-colors"
                >
                  View Best Sellers
                </Link>
              </div>

              {/* Trust badges */}
              <div className="pt-6 grid grid-cols-3 gap-4 border-t border-slate-800/80 max-w-md mx-auto lg:mx-0 text-left">
                <div>
                  <p className="text-lg font-bold text-white">100%</p>
                  <p className="text-xs text-slate-400">Genuine Brands</p>
                </div>
                <div>
                  <p className="text-lg font-bold text-white">Fast</p>
                  <p className="text-xs text-slate-400">Doorstep Delivery</p>
                </div>
                <div>
                  <p className="text-lg font-bold text-white">7 Days</p>
                  <p className="text-xs text-slate-400">Easy Replacement</p>
                </div>
              </div>
            </div>

            {/* Right Hero Visual Card */}
            <div className="lg:col-span-5 relative">
              <div className="relative mx-auto max-w-sm rounded-3xl bg-gradient-to-tr from-indigo-500/20 via-purple-500/10 to-transparent p-1 shadow-2xl">
                <div className="rounded-[22px] bg-slate-900 border border-slate-800 p-6 space-y-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-indigo-400 uppercase tracking-wider flex items-center gap-1.5">
                      <Zap size={14} className="fill-indigo-400" /> Featured Deal
                    </span>
                    <span className="px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-400 text-xs font-bold">
                      16% OFF
                    </span>
                  </div>

                  <div className="aspect-video rounded-xl bg-slate-950 border border-slate-800/80 flex items-center justify-center p-4">
                    <img
                      src="/uploads/products/nexora-n12-pro-5g-cosmic-grey-1.jpg"
                      alt="Nexora N-12 Pro 5G"
                      className="max-h-40 object-contain"
                      onError={(e) => { (e.target as HTMLElement).style.display = 'none'; }}
                    />
                  </div>

                  <div className="space-y-2">
                    <h3 className="font-bold text-white text-base">Nexora N-12 Pro 5G Flagship</h3>
                    <p className="text-xs text-slate-400 line-clamp-2">
                      AMOLED 120Hz display, 50MP Sony sensor, 67W Turbo charging and 5000 mAh battery.
                    </p>
                  </div>

                  <div className="flex items-center justify-between pt-2">
                    <div>
                      <div className="text-xl font-extrabold text-white">₹35,999</div>
                      <div className="text-xs text-slate-500 line-through">₹42,999</div>
                    </div>
                    <Link
                      to="/products/1"
                      className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold shadow-md transition-colors"
                    >
                      Buy Now
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Featured Categories Grid */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold text-white tracking-tight">Shop by Category</h2>
            <p className="text-xs text-slate-400 mt-1">Browse our hand-picked selections</p>
          </div>
          <Link to="/products" className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1">
            <span>View All</span> <ArrowRight size={14} />
          </Link>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          {categories.map((cat) => (
            <Link
              key={cat.id}
              to={`/products?categoryId=${cat.id}`}
              className="group p-4 rounded-2xl bg-slate-900 border border-slate-800 hover:border-indigo-500/50 transition-all text-center hover:shadow-lg hover:shadow-indigo-500/10 flex flex-col items-center gap-3"
            >
              <div className="w-14 h-14 rounded-2xl bg-indigo-600/10 group-hover:bg-indigo-600/20 text-indigo-400 flex items-center justify-center transition-colors">
                <TrendingUp size={24} />
              </div>
              <div>
                <h3 className="text-xs font-semibold text-slate-200 group-hover:text-indigo-400 transition-colors">
                  {cat.name}
                </h3>
                {cat.childCount !== undefined && cat.childCount > 0 && (
                  <p className="text-[10px] text-slate-500 mt-0.5">{cat.childCount} sub-categories</p>
                )}
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Trending / Featured Products Section */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <div className="flex items-center gap-2">
              <Sparkles size={18} className="text-indigo-400" />
              <h2 className="text-2xl font-bold text-white tracking-tight">Trending Highlights</h2>
            </div>
            <p className="text-xs text-slate-400 mt-1">Top-rated bestsellers loved by our community</p>
          </div>
          <Link to="/products?sort=rating" className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1">
            <span>See Top Rated</span> <ArrowRight size={14} />
          </Link>
        </div>

        {isLoading ? (
          <LoadingSpinner label="Loading catalogue..." />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {featuredProducts.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        )}
      </section>

      {/* Promotional Callout Banner */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="relative rounded-3xl overflow-hidden bg-gradient-to-r from-indigo-900 via-purple-900 to-slate-900 border border-indigo-800/50 p-8 sm:p-12 shadow-2xl">
          <div className="relative z-10 max-w-xl space-y-4">
            <span className="px-3 py-1 rounded-full bg-amber-400/20 text-amber-300 text-xs font-bold uppercase tracking-wider inline-block">
              Limited Time Coupon
            </span>
            <h2 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
              Get ₹200 Flat Discount on Your First Checkout!
            </h2>
            <p className="text-slate-300 text-xs sm:text-sm">
              Use code <strong className="text-white font-mono bg-black/40 px-2 py-0.5 rounded">WELCOME10</strong> at checkout to claim your savings. Valid across all electronics and lifestyle orders.
            </p>
            <div className="pt-2">
              <Link
                to="/products"
                className="px-6 py-3 bg-white hover:bg-slate-100 text-slate-950 font-bold rounded-xl text-xs inline-flex items-center gap-2 shadow-lg transition-colors"
              >
                <span>Shop With Coupon</span>
                <ArrowRight size={14} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Verified Customer Reviews */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-md mx-auto mb-10">
          <h2 className="text-2xl font-bold text-white tracking-tight">Loved by Customers</h2>
          <p className="text-xs text-slate-400 mt-1">Real feedback from verified purchasers</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-3">
            <div className="flex items-center gap-1 text-amber-400">
              {[...Array(5)].map((_, i) => (
                <Star key={i} size={15} className="fill-current" />
              ))}
            </div>
            <p className="text-xs text-slate-300 leading-relaxed italic">
              "The Nexora UltraBook exceeded all my expectations. Fast delivery, pristine packaging, and the battery life lasts a full work day without breaking a sweat."
            </p>
            <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs">
              <div>
                <p className="font-semibold text-white">Prasanta Kumar</p>
                <p className="text-[11px] text-slate-500">Bengaluru, Karnataka</p>
              </div>
              <span className="text-[11px] text-emerald-400 font-medium flex items-center gap-1">
                <CheckCircle size={12} /> Verified
              </span>
            </div>
          </div>

          <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-3">
            <div className="flex items-center gap-1 text-amber-400">
              {[...Array(5)].map((_, i) => (
                <Star key={i} size={15} className="fill-current" />
              ))}
            </div>
            <p className="text-xs text-slate-300 leading-relaxed italic">
              "Orbitrio Pulse Buds have stellar active noise cancellation. Seamless checkout, coupon worked instantly, and customer care responded within minutes."
            </p>
            <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs">
              <div>
                <p className="font-semibold text-white">Ananya Sharma</p>
                <p className="text-[11px] text-slate-500">Noida, UP</p>
              </div>
              <span className="text-[11px] text-emerald-400 font-medium flex items-center gap-1">
                <CheckCircle size={12} /> Verified
              </span>
            </div>
          </div>

          <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-3">
            <div className="flex items-center gap-1 text-amber-400">
              {[...Array(5)].map((_, i) => (
                <Star key={i} size={15} className="fill-current" />
              ))}
            </div>
            <p className="text-xs text-slate-300 leading-relaxed italic">
              "Clean UI, real-time inventory updates, and fast cash-on-delivery tracking. Easily the best e-commerce experience I've had this year."
            </p>
            <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs">
              <div>
                <p className="font-semibold text-white">Rohit Verma</p>
                <p className="text-[11px] text-slate-500">Mumbai, Maharashtra</p>
              </div>
              <span className="text-[11px] text-emerald-400 font-medium flex items-center gap-1">
                <CheckCircle size={12} /> Verified
              </span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
