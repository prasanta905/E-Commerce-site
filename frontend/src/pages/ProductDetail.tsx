import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  ShoppingCart,
  Heart,
  ShieldCheck,
  Truck,
  RotateCcw,
  Sparkles,
  Check,
  Star,
  CheckCircle,
  MessageSquare,
  ChevronRight,
} from 'lucide-react';
import { productService } from '../services/productService';
import { ProductDetail as ProductDetailType, Review } from '../types';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
import RatingStars from '../components/RatingStars';
import ProductCard from '../components/ProductCard';
import LoadingSpinner from '../components/LoadingSpinner';
import api from '../services/api';

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { addItem } = useCartStore();
  const { isAuthenticated, user } = useAuthStore();

  const [product, setProduct] = useState<ProductDetailType | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [activeImage, setActiveImage] = useState<string>('');
  const [quantity, setQuantity] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [isAdding, setIsAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [isWishlisted, setIsWishlisted] = useState(false);

  // Review form state
  const [showReviewForm, setShowReviewForm] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewTitle, setReviewTitle] = useState('');
  const [reviewBody, setReviewBody] = useState('');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState('');

  useEffect(() => {
    if (!id) return;
    const loadDetail = async () => {
      try {
        setIsLoading(true);
        const [detail, revs] = await Promise.all([
          productService.getProductById(Number(id)),
          productService.getReviews(Number(id)),
        ]);
        setProduct(detail);
        setReviews(revs || []);
        if (detail.images && detail.images.length > 0) {
          setActiveImage(detail.images[0]);
        }
      } catch (err) {
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };
    loadDetail();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [id]);

  if (isLoading) {
    return <LoadingSpinner label="Loading product specifications..." />;
  }

  if (!product) {
    return (
      <div className="max-w-md mx-auto py-24 text-center space-y-4">
        <h2 className="text-xl font-bold text-white">Product Not Found</h2>
        <p className="text-xs text-slate-400">The product you requested does not exist or has been discontinued.</p>
        <Link to="/products" className="inline-block px-4 py-2 bg-indigo-600 text-white rounded-xl text-xs font-semibold">
          Return to Catalogue
        </Link>
      </div>
    );
  }

  const handleAddToCart = async () => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    try {
      setIsAdding(true);
      await addItem(product.id, quantity);
      setAdded(true);
      setTimeout(() => setAdded(false), 2000);
    } finally {
      setIsAdding(false);
    }
  };

  const handleWishlistToggle = async () => {
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

  const handleReviewSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    try {
      setReviewSubmitting(true);
      setReviewError('');
      await productService.addReview(product.id, {
        rating: reviewRating,
        title: reviewTitle,
        body: reviewBody,
      });
      setShowReviewForm(false);
      setReviewTitle('');
      setReviewBody('');
      // Reload reviews
      const updated = await productService.getReviews(product.id);
      setReviews(updated);
    } catch (err: any) {
      setReviewError(err.response?.data?.error?.message || 'Failed to submit review');
    } finally {
      setReviewSubmitting(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-12">
      {/* Breadcrumbs */}
      <nav className="flex items-center gap-2 text-xs text-slate-400">
        <Link to="/" className="hover:text-white">Home</Link>
        <ChevronRight size={12} />
        <Link to="/products" className="hover:text-white">Products</Link>
        <ChevronRight size={12} />
        <Link to={`/products?categoryId=${product.categoryId}`} className="hover:text-white">{product.categoryName}</Link>
        <ChevronRight size={12} />
        <span className="text-slate-200 font-medium truncate max-w-xs">{product.name}</span>
      </nav>

      {/* Main Product Showcase */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
        {/* Left Gallery (5 cols) */}
        <div className="lg:col-span-5 space-y-4">
          <div className="relative aspect-square rounded-3xl bg-slate-900 border border-slate-800 overflow-hidden flex items-center justify-center p-6 shadow-2xl">
            {activeImage ? (
              <img
                src={activeImage}
                alt={product.name}
                className="w-full h-full object-contain"
                onError={(e) => { (e.target as HTMLElement).style.display = 'none'; }}
              />
            ) : (
              <div className="text-center text-slate-500">
                <ShoppingCart size={48} className="mx-auto mb-2 text-indigo-400 opacity-60" />
                <span className="text-xs">Premium Product Preview</span>
              </div>
            )}

            {product.discountPercent > 0 && (
              <span className="absolute top-4 left-4 px-3 py-1 rounded-xl bg-emerald-500/90 text-white text-xs font-bold shadow-md">
                {product.discountPercent}% OFF
              </span>
            )}
          </div>

          {/* Thumbnails */}
          {product.images && product.images.length > 1 && (
            <div className="flex gap-3 overflow-x-auto pb-2">
              {product.images.map((img: string, idx: number) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setActiveImage(img)}
                  className={`w-16 h-16 rounded-xl bg-slate-900 border overflow-hidden p-1 shrink-0 transition-all ${
                    activeImage === img ? 'border-indigo-500 ring-2 ring-indigo-500/30' : 'border-slate-800 opacity-70 hover:opacity-100'
                  }`}
                >
                  <img src={img} alt="" className="w-full h-full object-cover rounded-lg" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Right Info (7 cols) */}
        <div className="lg:col-span-7 space-y-6">
          {/* Brand & Category pill */}
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-1 rounded-lg bg-indigo-500/10 text-indigo-400 text-xs font-bold uppercase tracking-wider">
              {product.brandName}
            </span>
            <span className="text-xs text-slate-400">• {product.categoryName}</span>
          </div>

          {/* Title */}
          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight leading-tight">
            {product.name}
          </h1>

          {/* Rating overview */}
          <div className="flex items-center gap-3">
            <RatingStars rating={product.ratingAvg} count={product.ratingCount} size={16} />
            <span className="text-slate-600">|</span>
            <span className="text-xs text-emerald-400 font-semibold flex items-center gap-1">
              <CheckCircle size={13} /> Genuine & Verified Stock
            </span>
          </div>

          {/* Price breakdown */}
          <div className="p-4 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-1">
            <div className="flex items-baseline gap-3">
              <span className="text-3xl font-extrabold text-white">
                ₹{product.sellingPrice?.toLocaleString('en-IN')}
              </span>
              {product.mrp > product.sellingPrice && (
                <>
                  <span className="text-sm text-slate-500 line-through">
                    ₹{product.mrp?.toLocaleString('en-IN')}
                  </span>
                  <span className="text-xs font-bold text-emerald-400">
                    Save ₹{(product.mrp - product.sellingPrice).toLocaleString('en-IN')} ({product.discountPercent}%)
                  </span>
                </>
              )}
            </div>
            <p className="text-[11px] text-slate-400">Inclusive of all taxes • Free shipping on this order</p>
          </div>

          {/* Description */}
          <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
            {product.description}
          </p>

          {/* Inventory status & Purchase controls */}
          <div className="pt-4 border-t border-slate-800 space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-400 font-medium">Availability:</span>
              <span className={`text-xs font-bold px-2.5 py-1 rounded-md ${
                product.inStock
                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                  : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
              }`}>
                {product.inStock ? `In Stock (${product.availableStock} available)` : 'Currently Out of Stock'}
              </span>
            </div>

            {/* Quantity selector & CTA buttons */}
            <div className="flex flex-col sm:flex-row gap-3 pt-2">
              <div className="flex items-center border border-slate-800 rounded-xl bg-slate-900 px-3 py-1.5 w-fit">
                <button
                  type="button"
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  className="px-2 py-1 text-slate-400 hover:text-white font-bold"
                >
                  -
                </button>
                <span className="px-4 py-1 text-xs font-bold text-white min-w-[2.5rem] text-center">
                  {quantity}
                </span>
                <button
                  type="button"
                  onClick={() => setQuantity(quantity + 1)}
                  className="px-2 py-1 text-slate-400 hover:text-white font-bold"
                >
                  +
                </button>
              </div>

              <button
                type="button"
                onClick={handleAddToCart}
                disabled={isAdding}
                className={`flex-1 py-3 px-6 rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-lg transition-all ${
                  added
                    ? 'bg-emerald-600 text-white'
                    : 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-indigo-600/30 active:scale-98'
                }`}
              >
                {added ? (
                  <>
                    <Check size={16} /> Added to Shopping Cart!
                  </>
                ) : isAdding ? (
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                ) : (
                  <>
                    <ShoppingCart size={16} /> Add to Cart • ₹{(product.sellingPrice * quantity).toLocaleString('en-IN')}
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={handleWishlistToggle}
                className={`p-3 rounded-xl border transition-colors flex items-center justify-center ${
                  isWishlisted
                    ? 'bg-rose-500/10 border-rose-500 text-rose-400'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-rose-400 hover:bg-slate-850'
                }`}
                title="Wishlist"
              >
                <Heart size={20} className={isWishlisted ? 'fill-current' : ''} />
              </button>
            </div>
          </div>

          {/* Confidence guarantee badges */}
          <div className="grid grid-cols-3 gap-3 pt-4 text-center text-[11px] text-slate-400">
            <div className="p-3 rounded-xl bg-slate-900 border border-slate-800/80">
              <Truck size={16} className="mx-auto mb-1 text-indigo-400" />
              <span>Fast Track Delivery</span>
            </div>
            <div className="p-3 rounded-xl bg-slate-900 border border-slate-800/80">
              <ShieldCheck size={16} className="mx-auto mb-1 text-indigo-400" />
              <span>1 Year Warranty</span>
            </div>
            <div className="p-3 rounded-xl bg-slate-900 border border-slate-800/80">
              <RotateCcw size={16} className="mx-auto mb-1 text-indigo-400" />
              <span>7 Days Return</span>
            </div>
          </div>
        </div>
      </div>

      {/* Specifications Table */}
      {product.specifications && Object.keys(product.specifications).length > 0 && (
        <section className="space-y-4 pt-6 border-t border-slate-800">
          <h3 className="text-lg font-bold text-white tracking-tight">Technical Specifications</h3>
          <div className="rounded-2xl border border-slate-800 overflow-hidden divide-y divide-slate-800">
            {Object.entries(product.specifications).map(([key, value], idx) => (
              <div key={key} className={`grid grid-cols-3 p-3.5 text-xs ${idx % 2 === 0 ? 'bg-slate-900/60' : 'bg-slate-900/30'}`}>
                <span className="font-semibold text-slate-400 uppercase tracking-wider">{key}</span>
                <span className="col-span-2 text-slate-200">{String(value)}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Customer Reviews Section */}
      <section className="space-y-6 pt-6 border-t border-slate-800">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-bold text-white tracking-tight">Customer Reviews</h3>
            <p className="text-xs text-slate-400 mt-0.5">{reviews.length} community ratings & thoughts</p>
          </div>

          <button
            type="button"
            onClick={() => setShowReviewForm(!showReviewForm)}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors"
          >
            <MessageSquare size={14} /> Write a Review
          </button>
        </div>

        {/* Write Review Form */}
        {showReviewForm && (
          <form onSubmit={handleReviewSubmit} className="p-5 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
            <h4 className="text-sm font-bold text-white">Share your experience</h4>
            {reviewError && (
              <p className="text-xs text-rose-400 bg-rose-500/10 p-2.5 rounded-lg border border-rose-500/20">{reviewError}</p>
            )}
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-300">Rating (1 to 5 Stars)</label>
              <div className="flex items-center gap-2">
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setReviewRating(star)}
                    className="p-1 text-amber-400 hover:scale-110 transition-transform"
                  >
                    <Star size={22} className={star <= reviewRating ? 'fill-amber-400' : 'text-slate-700'} />
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-300">Review Title</label>
              <input
                type="text"
                required
                placeholder="e.g. Absolutely loved the speed and design!"
                value={reviewTitle}
                onChange={(e) => setReviewTitle(e.target.value)}
                className="w-full mt-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-300">Review Details</label>
              <textarea
                rows={3}
                required
                placeholder="Explain what you liked or disliked about this product..."
                value={reviewBody}
                onChange={(e) => setReviewBody(e.target.value)}
                className="w-full mt-1 bg-slate-950 border border-slate-800 rounded-xl p-3 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="flex items-center gap-2 pt-1">
              <button
                type="submit"
                disabled={reviewSubmitting}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold disabled:opacity-50"
              >
                {reviewSubmitting ? 'Submitting...' : 'Submit Review'}
              </button>
              <button
                type="button"
                onClick={() => setShowReviewForm(false)}
                className="px-4 py-2 bg-slate-800 text-slate-400 hover:text-white rounded-xl text-xs font-medium"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* Reviews list */}
        {reviews.length === 0 ? (
          <p className="text-xs text-slate-500 italic">No reviews yet for this product. Be the first to review!</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {reviews.map((r) => (
              <div key={r.id} className="p-4 rounded-2xl bg-slate-900 border border-slate-800 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-full bg-indigo-600/20 text-indigo-400 font-bold text-xs flex items-center justify-center">
                      {r.authorName?.charAt(0) || 'U'}
                    </div>
                    <div>
                      <span className="text-xs font-semibold text-slate-200">{r.authorName}</span>
                      {r.isVerified && (
                        <span className="ml-2 text-[10px] text-emerald-400 font-medium inline-flex items-center gap-0.5">
                          <CheckCircle size={10} /> Verified Purchase
                        </span>
                      )}
                    </div>
                  </div>
                  <RatingStars rating={r.rating} size={13} />
                </div>
                {r.title && <h5 className="text-xs font-bold text-white">{r.title}</h5>}
                <p className="text-xs text-slate-300 leading-relaxed">{r.body}</p>
                <p className="text-[10px] text-slate-500 pt-1">
                  {new Date(r.createdAt).toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' })}
                </p>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Related Products */}
      {product.related && product.related.length > 0 && (
        <section className="space-y-6 pt-6 border-t border-slate-800">
          <h3 className="text-lg font-bold text-white tracking-tight">You Might Also Like</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
            {product.related.map((rp: any) => (
              <ProductCard key={rp.id} product={rp} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
