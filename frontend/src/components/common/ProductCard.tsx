import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShoppingCart, Heart, Check, Sparkles } from 'lucide-react';
import { ProductCard as ProductCardType } from '../../types';
import { useCartStore } from '../../store/cartStore';
import { useAuthStore } from '../../store/authStore';
import RatingStars from './RatingStars';
import api from '../../services/api';

interface ProductCardProps {
  product: ProductCardType;
  onWishlistToggle?: () => void;
}

export default function ProductCard({ product }: ProductCardProps) {
  const navigate = useNavigate();
  const { addItem } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const [isAdding, setIsAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [isWishlisted, setIsWishlisted] = useState(false);

  const handleAddToCart = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    try {
      setIsAdding(true);
      await addItem(product.id, 1);
      setAdded(true);
      setTimeout(() => setAdded(false), 1800);
    } catch (err) {
      console.error(err);
    } finally {
      setIsAdding(false);
    }
  };

  const handleWishlist = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    try {
      if (isWishlisted) {
        await api.delete(`/wishlist/items/${product.id}`);
        setIsWishlisted(false);
      } else {
        await api.post('/wishlist/items', { productId: product.id });
        setIsWishlisted(true);
      }
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="group relative bg-slate-900 border border-slate-800 hover:border-indigo-500/50 rounded-2xl overflow-hidden transition-all duration-300 hover:shadow-xl hover:shadow-indigo-500/10 flex flex-col h-full">
      {/* Badges */}
      <div className="absolute top-3 left-3 z-10 flex flex-col gap-1.5">
        {!!product.discountPercent && product.discountPercent > 0 && (
          <span className="px-2.5 py-1 text-xs font-bold rounded-lg bg-emerald-500/90 text-white shadow-sm backdrop-blur-md">
            {product.discountPercent}% OFF
          </span>
        )}
        {product.isNew && (
          <span className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-indigo-600/90 text-white shadow-sm flex items-center gap-1 backdrop-blur-md">
            <Sparkles size={11} /> New
          </span>
        )}
      </div>

      {/* Wishlist Button */}
      <button
        type="button"
        onClick={handleWishlist}
        className={`absolute top-3 right-3 z-10 w-9 h-9 rounded-full flex items-center justify-center transition-colors shadow-md backdrop-blur-md ${
          isWishlisted
            ? 'bg-rose-500 text-white'
            : 'bg-slate-950/70 text-slate-300 hover:text-rose-400 hover:bg-slate-900'
        }`}
        title={isWishlisted ? 'Remove from wishlist' : 'Add to wishlist'}
      >
        <Heart size={18} className={isWishlisted ? 'fill-current' : ''} />
      </button>

      {/* Image container */}
      <Link to={`/products/${product.id}`} className="block relative aspect-square bg-slate-950 overflow-hidden">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500"
            onError={(e) => {
              // fallback graphic
              (e.target as HTMLElement).style.display = 'none';
            }}
          />
        ) : null}
        {/* Placeholder banner if image missing */}
        <div className="w-full h-full flex flex-col items-center justify-center bg-gradient-to-br from-slate-900 to-indigo-950/40 text-slate-500 p-6 text-center">
          <div className="w-14 h-14 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 mb-2">
            <ShoppingCart size={24} />
          </div>
          <span className="text-xs font-medium text-slate-400 line-clamp-1">{product.brandName || 'ShopSphere'}</span>
        </div>
      </Link>

      {/* Content */}
      <div className="p-4 flex flex-col flex-1">
        {/* Brand & Category */}
        <div className="flex items-center justify-between text-xs text-slate-400 mb-1.5">
          <span className="font-medium text-indigo-400">{product.brandName}</span>
          <span>{product.categoryName}</span>
        </div>

        {/* Product Title */}
        <Link to={`/products/${product.id}`} className="block">
          <h3 className="text-sm font-semibold text-slate-100 hover:text-indigo-400 line-clamp-2 transition-colors mb-2 min-h-[2.5rem]">
            {product.name}
          </h3>
        </Link>

        {/* Rating */}
        <div className="mb-3">
          <RatingStars rating={product.ratingAvg || 0} count={product.ratingCount || 0} size={14} />
        </div>

        {/* Price & CTA */}
        <div className="mt-auto pt-3 border-t border-slate-800/80 flex items-center justify-between gap-2">
          <div>
            <div className="flex items-baseline gap-1.5">
              <span className="text-lg font-bold text-white">
                ₹{product.sellingPrice?.toLocaleString('en-IN')}
              </span>
              {product.mrp > product.sellingPrice && (
                <span className="text-xs text-slate-500 line-through">
                  ₹{product.mrp?.toLocaleString('en-IN')}
                </span>
              )}
            </div>
            <p className="text-[11px] font-medium text-emerald-400">
              {product.inStock ? 'In Stock' : 'Available on order'}
            </p>
          </div>

          <button
            type="button"
            onClick={handleAddToCart}
            disabled={isAdding}
            className={`px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm ${
              added
                ? 'bg-emerald-600 text-white'
                : 'bg-indigo-600 hover:bg-indigo-500 active:scale-95 text-white shadow-indigo-600/20'
            }`}
          >
            {added ? (
              <>
                <Check size={14} /> Added
              </>
            ) : isAdding ? (
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <>
                <ShoppingCart size={14} /> Add
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
