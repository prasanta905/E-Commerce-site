import { Star } from 'lucide-react';

interface RatingStarsProps {
  rating: number;
  count?: number;
  size?: number;
}

export default function RatingStars({ rating, count, size = 16 }: RatingStarsProps) {
  const fullStars = Math.floor(rating);
  const hasHalf = rating - fullStars >= 0.4;

  return (
    <div className="flex items-center gap-1.5 text-amber-400">
      <div className="flex items-center">
        {[...Array(5)].map((_, i) => (
          <Star
            key={i}
            size={size}
            className={`${
              i < fullStars
                ? 'fill-amber-400 text-amber-400'
                : i === fullStars && hasHalf
                ? 'fill-amber-400/50 text-amber-400'
                : 'text-slate-600'
            }`}
          />
        ))}
      </div>
      <span className="text-xs font-semibold text-slate-300">{rating.toFixed(1)}</span>
      {count !== undefined && (
        <span className="text-xs text-slate-400">({count.toLocaleString()})</span>
      )}
    </div>
  );
}
