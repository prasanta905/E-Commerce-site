# ShopSphere — REST API Contract `/api/v1`

All responses:
```json
{
  "success": true,
  "data": { ... },
  "error": null,          // omitted when success
  "meta": { "page": 0, "size": 20, "totalPages": 3, "totalElements": 57 }
}
```
Errors use `ApiError { timestamp, status, errorCode, message, path, fieldErrors? }`.
Auth = `Authorization: Bearer <jwt>`. Admin endpoints require ROLE_USER+ROLE_ADMIN.

## Authentication — public
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | /auth/register | {fullName, email, password, phone} | 201 {user} + sets refresh cookie |
| POST | /auth/login | {email, password} | 200 {accessToken, user} + refresh cookie |
| POST | /auth/refresh | (cookie) | 200 {accessToken} rotates refresh |
| POST | /auth/logout | — | 204 clears cookie, revokes token family |
| POST | /auth/forgot-password | {email} | 202 always (no user enumeration) |
| POST | /auth/reset-password | {token, newPassword} | 200 or 400 |
| POST | /auth/verify-email | {token} | 200 |

## Products — public
| GET | /products?page&size&search&minPrice&maxPrice&categoryIds&brandIds&minRating&inStock&sort(price,price_desc,rating, newest, popularity) | paged ProductCardDto |
| GET | /products/{id} | ProductDetailDto + related |
| GET | /products/featured | paged |
| GET | /products/search?q= | suggestions (max 8 names + ids) |
| GET | /products/category/{categoryId} | paged incl. child categories |
| GET | /products/brand/{brandId} | paged |
| GET | /products/{productId}/reviews?page | paged ReviewDto |
| POST | /products/{productId}/reviews 🔒 | {rating, title, body} — verified-purchase logic |
| PATCH | /products/{productId}/reviews/{reviewId} 🔒 owner only |

## Categories/Brands — public
GET /categories, GET /categories/{id}, GET /brands, GET /brands/{id}

## Cart 🔒
| GET | /cart | CartDto {items[], subtotal, itemCount} |
| POST | /cart/items | {productId, quantity} |
| PUT | /cart/items/{itemId} | {quantity} |
| DELETE | /cart/items/{itemId} |
| DELETE | /cart |

## Wishlist 🔒
GET /wishlist · POST /wishlist/items {productId} · DELETE /wishlist/items/{productId}

## User/Addresses 🔒
GET/PUT /users/me — view/update profile
/additional | POST /users/me/password — change password 🔒
GET/POST/PUT/DELETE /users/me/addresses[/id]

## Checkout 🔒
| GET | /checkout/shipping-methods | - | [{id, code, name, baseFee, freeAbove}] — code-defined catalogue |
| POST | /checkout/preview | {addressId, shippingMethodId, couponCode?} | CheckoutPreviewDto {subtotal, discount, shippingFee, total, shippingMethodName, stockIssues[]} |
| POST | /checkout | {addressId, shippingMethodId, couponCode?, paymentMethod (COD\|RAZORPAY), idempotencyKey, termsAccepted: "true"} | creates order (PENDING if prepaid, CONFIRMED if COD) → {orderId} — repeated idempotencyKey returns the same order |

## Orders 🔒
GET /orders?page (own only) · GET /orders/{id} · POST /orders/{id}/cancel · POST /orders/{id}/return-request {reason} · GET /orders/{id}/invoice (PDF, owner-only, unavailable while PENDING/CANCELLED)

## Payments 🔒
POST /payments/orders/{orderId}/create → PaymentSetupDto {gatewayOrderId, keyId, amount, currency, orderNumber, paymentStatus} — owner-scoped, idempotent, 503 RAZORPAY_NOT_CONFIGURED when disabled
POST /payments/verify {razorpayOrderId, razorpayPaymentId, razorpaySignature} → HMAC(gatewayOrderId|paymentId) verify → order CONFIRMED → {orderStatus, paymentStatus} (idempotent for already-paid)
POST /payments/webhook — raw Razorpay payload HMAC-verified via X-Razorpay-Signature (idempotent settlement)

## Admin 🔒 ROLE_ADMIN
GET /admin/dashboard · GET /admin/analytics?period=daily|weekly|monthly|yearly&from&to
/products CRUD: POST, PUT /{id}, DELETE /{id} (soft), PATCH /{id}/active
POST /admin/products/{id}/images (multipart, safe-upload)
/categories, /brands CRUD (DELETE blocked when children/deps exist)
GET /admin/orders?status&q&page — search/filter, PUT /admin/orders/{id}/status {toStatus, note} — state machine enforced
GET /admin/customers?page&q&active — profile list, no password field ever
PUT /admin/customers/{id}/active {isActive}
/inventory: GET /admin/inventory?low=, PUT /admin/inventory/{productId} {delta, reason} — audited
/coupons CRUD, /admin/returns list + approve/reject + refund
GET /admin/reviews — moderation

## Operational
GET /actuator/health, /actuator/info (prod-restricted), /swagger-ui.html, /v3/api-docs
