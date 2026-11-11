import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Heart, ShoppingBag, Trash2, ArrowRight, Package } from 'lucide-react';
import { wishlistService } from '../services/wishlistService';
import { useCartStore } from '../store/cartStore';
import type { ProductCard } from '../types';
import LoadingSpinner from '../components/LoadingSpinner';

const Wishlist: React.FC = () => {
  const [items, setItems] = useState<ProductCard[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [movingId, setMovingId] = useState<number | null>(null);

  const addItemToCart = useCartStore((state) => state.addItem);

  const fetchWishlist = async () => {
    try {
      setLoading(true);
      setError(null);
      const list = await wishlistService.getWishlist();
      setItems(list);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load your wishlist.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWishlist();
  }, []);

  const handleRemove = async (productId: number) => {
    try {
      await wishlistService.removeFromWishlist(productId);
      setItems((prev) => prev.filter((p) => p.id !== productId));
    } catch (err: any) {
      alert(err.response?.data?.message || 'Could not remove item.');
    }
  };

  const handleMoveToCart = async (product: ProductCard) => {
    try {
      setMovingId(product.id);
      await addItemToCart(product.id, 1);
      await wishlistService.removeFromWishlist(product.id);
      setItems((prev) => prev.filter((p) => p.id !== product.id));
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to add item to cart.');
    } finally {
      setMovingId(null);
    }
  };

  if (loading) {
    return <LoadingSpinner fullPage text="Loading your saved wishlist..." />;
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight flex items-center gap-3">
            <Heart className="w-7 h-7 text-rose-500 fill-rose-500" /> My Wishlist
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            {items.length} {items.length === 1 ? 'item' : 'items'} saved for later
          </p>
        </div>
        <Link
          to="/products"
          className="inline-flex items-center gap-2 text-sm font-semibold text-indigo-600 hover:text-indigo-700"
        >
          Continue Shopping <ArrowRight className="w-4 h-4" />
        </Link>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 text-rose-700 rounded-2xl mb-6 text-sm">
          {error}
        </div>
      )}

      {items.length === 0 ? (
        <div className="bg-white rounded-3xl p-12 text-center border border-slate-100 shadow-sm max-w-lg mx-auto">
          <div className="w-16 h-16 bg-rose-50 text-rose-400 rounded-full flex items-center justify-center mx-auto mb-4">
            <Heart className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-slate-800 mb-2">Your wishlist is empty</h2>
          <p className="text-slate-500 text-sm mb-6">
            Explore our collection and tap the heart icon on any item you love!
          </p>
          <Link
            to="/products"
            className="inline-flex items-center justify-center px-6 py-3 rounded-xl bg-indigo-600 text-white font-semibold text-sm hover:bg-indigo-700 shadow-sm transition"
          >
            Explore Catalog
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {items.map((product) => (
            <div
              key={product.id}
              className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden flex flex-col hover:border-slate-300 transition group"
            >
              <div className="relative aspect-square bg-slate-50 overflow-hidden">
                <Link to={`/products/${product.id}`}>
                  {product.primaryImageUrl ? (
                    <img
                      src={product.primaryImageUrl}
                      alt={product.name}
                      className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-slate-300">
                      <Package className="w-12 h-12" />
                    </div>
                  )}
                </Link>
                <button
                  onClick={() => handleRemove(product.id)}
                  title="Remove from Wishlist"
                  className="absolute top-3 right-3 w-8 h-8 rounded-full bg-white/90 backdrop-blur-sm text-slate-400 hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center shadow-sm transition"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>

              <div className="p-4 flex-1 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between text-xs text-slate-500 mb-1">
                    <span>{product.brandName}</span>
                    <span className="capitalize">{product.categoryName}</span>
                  </div>
                  <Link
                    to={`/products/${product.id}`}
                    className="text-sm font-semibold text-slate-800 hover:text-indigo-600 line-clamp-2 leading-snug"
                  >
                    {product.name}
                  </Link>
                </div>

                <div className="pt-4 mt-auto">
                  <div className="flex items-baseline gap-2 mb-3">
                    <span className="text-base font-bold text-slate-900">
                      ₹{product.price.toLocaleString('en-IN')}
                    </span>
                    {product.originalPrice && product.originalPrice > product.price && (
                      <span className="text-xs text-slate-400 line-through">
                        ₹{product.originalPrice.toLocaleString('en-IN')}
                      </span>
                    )}
                  </div>

                  <button
                    onClick={() => handleMoveToCart(product)}
                    disabled={movingId === product.id}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs shadow-sm transition disabled:opacity-50"
                  >
                    <ShoppingBag className="w-4 h-4" />
                    {movingId === product.id ? 'Moving...' : 'Move to Cart'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Wishlist;
