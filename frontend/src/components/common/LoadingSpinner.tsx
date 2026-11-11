import React from 'react';

interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  label?: string;
  text?: string;
  fullPage?: boolean;
}

export default function LoadingSpinner({
  size = 'md',
  label,
  text,
  fullPage = false,
}: LoadingSpinnerProps) {
  const displayText = text || label;
  const sizeClass = {
    sm: 'w-5 h-5 border-2',
    md: 'w-8 h-8 border-3',
    lg: 'w-12 h-12 border-4',
  }[size];

  const content = (
    <div className="flex flex-col items-center justify-center p-8 gap-3">
      <div
        className={`${sizeClass} border-indigo-500/20 border-t-indigo-500 rounded-full animate-spin`}
      />
      {displayText && (
        <p className="text-sm font-medium text-slate-500 animate-pulse">{displayText}</p>
      )}
    </div>
  );

  if (fullPage) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        {content}
      </div>
    );
  }

  return content;
}
