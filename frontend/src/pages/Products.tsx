import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Filter,
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  Search,
} from 'lucide-react';
import { productService, ProductFilterParams } from '../services/productService';
import { ProductCard as ProductCardType, Category, Brand, PageResponse } from '../types';
import ProductCard from '../components/ProductCard';
import LoadingSpinner from '../components/LoadingSpinner';

export default function Products() {
  const [searchParams, setSearchParams] = useSearchParams();

  const [products, setProducts] = useState<ProductCardType[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [brands, setBrands] = useState<Brand[]>([]);
  const [meta, setMeta] = useState<PageResponse>({ page: 0, size: 12, totalPages: 1, totalElements: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);

  // Filter state
  const search = searchParams.get('search') || '';
  const categoryId = searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined;
  const brandId = searchParams.get('brandId') ? Number(searchParams.get('brandId')) : undefined;
  const sort = searchParams.get('sort') || 'popularity';
  const page = searchParams.get('page') ? Number(searchParams.get('page')) : 0;
  const inStock = searchParams.get('inStock') === 'true';
  const minPrice = searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined;
  const maxPrice = searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined;

  // Load categories & brands once
  useEffect(() => {
    Promise.all([productService.getCategories(), productService.getBrands()]).then(([cats, brs]) => {
      setCategories(cats || []);
      setBrands(brs || []);
    });
  }, []);

  // Fetch products on filter change
  useEffect(() => {
    const fetchFiltered = async () => {
      try {
        setIsLoading(true);
        const params: ProductFilterParams = {
          search: search || undefined,
          categoryId,
          brandId,
          sort,
          page,
          size: 12,
          inStock: inStock || undefined,
          minPrice,
          maxPrice,
        };
        const data = await productService.getProducts(params);
        setProducts(data.items || []);
        setMeta(data.meta || { page: 0, size: 12, totalPages: 1, totalElements: 0 });
      } catch (err) {
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchFiltered();
  }, [search, categoryId, brandId, sort, page, inStock, minPrice, maxPrice]);

  const updateFilter = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(searchParams);
    if (value === undefined || value === '') {
      next.delete(key);
    } else {
      next.set(key, value);
    }
    next.set('page', '0'); // reset to page 0 on filter change
    setSearchParams(next);
  };

  const resetFilters = () => {
    setSearchParams(new URLSearchParams());
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-800">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Explore Products</h1>
          <p className="text-xs text-slate-400 mt-1">
            {meta.totalElements} premium items available
            {search && <span> matching "<strong className="text-white">{search}</strong>"</span>}
          </p>
        </div>

        {/* Controls */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setMobileFilterOpen(!mobileFilterOpen)}
            className="md:hidden px-3 py-2 bg-slate-900 border border-slate-800 rounded-xl text-xs font-semibold text-slate-300 flex items-center gap-2"
          >
            <SlidersHorizontal size={15} /> Filters
          </button>

          <div className="flex items-center gap-2 text-xs">
            <span className="text-slate-400 font-medium">Sort by:</span>
            <select
              value={sort}
              onChange={(e) => updateFilter('sort', e.target.value)}
              className="bg-slate-900 border border-slate-800 text-slate-200 text-xs rounded-xl px-3 py-2 focus:outline-none focus:border-indigo-500"
            >
              <option value="popularity">Popularity</option>
              <option value="price_asc">Price: Low to High</option>
              <option value="price_desc">Price: High to Low</option>
              <option value="rating">Highest Rated</option>
              <option value="newest">Newest Arrivals</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main layout */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-8 items-start">
        {/* Filters Sidebar (Desktop) */}
        <aside
          className={`md:block space-y-6 p-5 rounded-2xl bg-slate-900 border border-slate-800 text-xs ${
            mobileFilterOpen ? 'block' : 'hidden'
          }`}
        >
          <div className="flex items-center justify-between pb-3 border-b border-slate-800">
            <span className="font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
              <Filter size={14} className="text-indigo-400" /> Filters
            </span>
            <button
              type="button"
              onClick={resetFilters}
              className="text-slate-400 hover:text-indigo-400 flex items-center gap-1 text-[11px]"
            >
              <RotateCcw size={11} /> Reset
            </button>
          </div>

          {/* Category filter */}
          <div>
            <h4 className="font-semibold text-slate-300 mb-2">Category</h4>
            <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
              <button
                type="button"
                onClick={() => updateFilter('categoryId', undefined)}
                className={`block w-full text-left px-2 py-1.5 rounded-lg transition-colors ${
                  categoryId === undefined
                    ? 'bg-indigo-600/20 text-indigo-400 font-bold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                All Categories
              </button>
              {categories.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => updateFilter('categoryId', c.id.toString())}
                  className={`block w-full text-left px-2 py-1.5 rounded-lg transition-colors ${
                    categoryId === c.id
                      ? 'bg-indigo-600/20 text-indigo-400 font-bold'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {c.name}
                </button>
              ))}
            </div>
          </div>

          {/* Brand filter */}
          <div>
            <h4 className="font-semibold text-slate-300 mb-2">Brand</h4>
            <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
              <button
                type="button"
                onClick={() => updateFilter('brandId', undefined)}
                className={`block w-full text-left px-2 py-1.5 rounded-lg transition-colors ${
                  brandId === undefined
                    ? 'bg-indigo-600/20 text-indigo-400 font-bold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                All Brands
              </button>
              {brands.map((b) => (
                <button
                  key={b.id}
                  type="button"
                  onClick={() => updateFilter('brandId', b.id.toString())}
                  className={`block w-full text-left px-2 py-1.5 rounded-lg transition-colors ${
                    brandId === b.id
                      ? 'bg-indigo-600/20 text-indigo-400 font-bold'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {b.name}
                </button>
              ))}
            </div>
          </div>

          {/* Price Range */}
          <div>
            <h4 className="font-semibold text-slate-300 mb-2">Price (₹)</h4>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="number"
                placeholder="Min"
                defaultValue={minPrice || ''}
                onBlur={(e) => updateFilter('minPrice', e.target.value || undefined)}
                className="bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              />
              <input
                type="number"
                placeholder="Max"
                defaultValue={maxPrice || ''}
                onBlur={(e) => updateFilter('maxPrice', e.target.value || undefined)}
                className="bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          {/* In Stock Only */}
          <div className="pt-2 border-t border-slate-800">
            <label className="flex items-center gap-2 cursor-pointer text-slate-300 select-none">
              <input
                type="checkbox"
                checked={inStock}
                onChange={(e) => updateFilter('inStock', e.target.checked ? 'true' : undefined)}
                className="rounded border-slate-800 text-indigo-600 focus:ring-0 bg-slate-950"
              />
              <span>In Stock Only</span>
            </label>
          </div>
        </aside>

        {/* Product Grid Area */}
        <div className="md:col-span-3 space-y-8">
          {isLoading ? (
            <LoadingSpinner label="Fetching matching products..." />
          ) : products.length === 0 ? (
            <div className="text-center py-20 p-8 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
              <div className="w-12 h-12 bg-slate-800 text-slate-400 rounded-2xl flex items-center justify-center mx-auto">
                <Search size={22} />
              </div>
              <h3 className="text-base font-bold text-white">No products found</h3>
              <p className="text-xs text-slate-400 max-w-sm mx-auto">
                We couldn't find any products matching your selected filters. Try resetting the filters or searching for something else.
              </p>
              <button
                type="button"
                onClick={resetFilters}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold transition-colors"
              >
                Clear All Filters
              </button>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                {products.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>

              {/* Pagination */}
              {meta.totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 pt-6 border-t border-slate-800">
                  <button
                    type="button"
                    disabled={meta.page <= 0}
                    onClick={() => updateFilter('page', (meta.page - 1).toString())}
                    className="p-2 rounded-xl bg-slate-900 border border-slate-800 text-slate-300 disabled:opacity-40 hover:bg-slate-800 transition-colors"
                  >
                    <ChevronLeft size={16} />
                  </button>

                  {[...Array(meta.totalPages)].map((_, i) => (
                    <button
                      key={i}
                      type="button"
                      onClick={() => updateFilter('page', i.toString())}
                      className={`w-9 h-9 rounded-xl text-xs font-bold transition-all ${
                        meta.page === i
                          ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                          : 'bg-slate-900 border border-slate-800 text-slate-300 hover:bg-slate-800'
                      }`}
                    >
                      {i + 1}
                    </button>
                  ))}

                  <button
                    type="button"
                    disabled={meta.page >= meta.totalPages - 1}
                    onClick={() => updateFilter('page', (meta.page + 1).toString())}
                    className="p-2 rounded-xl bg-slate-900 border border-slate-800 text-slate-300 disabled:opacity-40 hover:bg-slate-800 transition-colors"
                  >
                    <ChevronRight size={16} />
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
