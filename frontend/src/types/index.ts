export interface User {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  emailVerified: boolean;
  isActive?: boolean;
  createdAt?: string;
}

export interface AuthResponse {
  accessToken: string;
  email: string;
  fullName: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  emailVerified: boolean;
}

export interface ProductCard {
  id: number;
  name: string;
  slug?: string;
  sellingPrice: number;
  price: number;
  mrp: number;
  originalPrice?: number;
  discountPercent?: number;
  ratingAvg?: number;
  ratingCount?: number;
  imageUrl?: string;
  primaryImageUrl?: string;
  categoryName?: string;
  brandName?: string;
  inStock?: boolean;
  isNew?: boolean;
}

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  description: string;
  sellingPrice: number;
  mrp: number;
  discountPercent: number;
  ratingAvg: number;
  ratingCount: number;
  images: string[];
  specifications: Record<string, string>;
  categoryId: number;
  categoryName: string;
  brandId: number;
  brandName: string;
  isFeatured: boolean;
  availableStock: number;
  lowStockThreshold: number;
  inStock: boolean;
  related: ProductCard[];
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  imageUrl?: string;
  parentId?: number;
  childCount?: number;
  isActive?: boolean;
  subcategories?: Category[];
}

export interface Brand {
  id: number;
  name: string;
  slug: string;
  logoUrl?: string;
  isActive?: boolean;
}

export interface CartItem {
  itemId: number;
  productId: number;
  productName: string;
  productSlug: string;
  productImageUrl?: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  availableStock: number;
  inStock: boolean;
}

export interface Cart {
  items: CartItem[];
  subtotal: number;
  itemCount: number;
}

export interface WishlistItem {
  productId: number;
  name: string;
  slug: string;
  sellingPrice: number;
  mrp: number;
  imageUrl?: string;
  inStock: boolean;
  addedAt: string;
}

export interface Wishlist {
  items: WishlistItem[];
}

export interface Address {
  id: number;
  contactName?: string;
  fullName?: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode?: string;
  postalCode?: string;
  country?: string;
  isDefault: boolean;
}

export interface OrderItemLine {
  productId: number;
  productName: string;
  productImageUrl?: string;
  productImage?: string;
  quantity: number;
  unitPrice: number;
  totalPrice?: number;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: string;
  paymentMethod?: string;
  paymentStatus?: string;
  totalAmount: number;
  grandTotal: number;
  itemCount: number;
  createdAt: string;
  items?: OrderItemLine[];
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: string;
  paymentMethod?: string;
  paymentStatus?: string;
  subtotal: number;
  discountAmount: number;
  shippingFee: number;
  totalAmount: number;
  grandTotal: number;
  createdAt: string;
  address?: Record<string, string>;
  shippingAddress?: {
    fullName: string;
    phone: string;
    line1: string;
    line2?: string;
    city: string;
    state: string;
    postalCode?: string;
    pincode?: string;
  };
  items: Array<{
    id: number;
    productId: number;
    productName: string;
    productImageUrl?: string;
    productImage?: string;
    unitPrice: number;
    quantity: number;
    lineTotal?: number;
    totalPrice?: number;
  }>;
  statusHistory?: Array<{
    status?: string;
    toStatus?: string;
    changedBy?: string;
    note?: string;
    createdAt: string;
  }>;
}

export interface CouponApplication {
  couponId: number;
  code: string;
  discountAmount: number;
  message: string;
}

export interface CheckoutPreview {
  subtotal: number;
  discount: number;
  shippingFee: number;
  total: number;
  couponCode?: string;
  shippingMethodName: string;
  stockIssues: string[];
}

export interface Review {
  id: number;
  productId: number;
  authorName: string;
  rating: number;
  title?: string;
  body?: string;
  isVerified: boolean;
  createdAt: string;
}

export interface AdminDashboard {
  totalCustomers: number;
  newCustomersToday: number;
  activeProducts: number;
  ordersToday: number;
  revenueTotal: number;
  revenueToday: number;
  revenueMonth: number;
  ordersByStatus: Record<string, number>;
  lowStockCount: number;
  pendingReturns: number;
}

export interface AdminAnalyticsPoint {
  bucket: string;
  orders: number;
  revenue: number;
}

export interface AdminAnalytics {
  period: string;
  from: string;
  to: string;
  points: AdminAnalyticsPoint[];
  averageOrderValue: number;
  totalRevenue: number;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug?: string;
  description?: string;
  sellingPrice: number;
  price: number;
  mrp: number;
  originalPrice?: number;
  ratingAvg?: number;
  ratingCount?: number;
  primaryImage?: string;
  primaryImageUrl?: string;
  categoryId?: number;
  categoryName?: string;
  brandId?: number;
  brandName?: string;
  isFeatured: boolean;
  isActive: boolean;
  stockQuantity?: number;
  reservedQuantity?: number;
  lowStockThreshold?: number;
}

export interface AdminInventoryItem {
  inventoryId?: number;
  productId: number;
  productName: string;
  sku: string;
  totalQuantity: number;
  reservedQuantity: number;
  quantityReserved: number;
  availableQuantity: number;
  quantityAvailable: number;
  lowStockThreshold: number;
  isLowStock: boolean;
}

export interface AdminCustomer {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  isActive: boolean;
  isEmailVerified: boolean;
  emailVerified?: boolean;
  roles?: string[];
  createdAt: string;
}

export interface AdminCoupon {
  id: number;
  code: string;
  discountType: 'PERCENT' | 'FIXED';
  discountValue: number;
  minOrderAmount: number;
  maxDiscountAmount?: number;
  expiresAt: string;
  maxGlobalUses?: number;
  maxUsesPerUser: number;
  currentUses?: number;
  isActive: boolean;
}

export interface AdminReturn {
  id: number;
  orderId: number;
  orderNumber: string;
  customerEmail: string;
  reason?: string;
  status: string;
  createdAt: string;
}

export interface PageResponse {
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: {
    code: string;
    message: string;
  };
}
