"use client";

import { useEffect, useState } from "react";
import { UtensilsCrossed } from "lucide-react";

/**
 * Fixed-size frame so a slow or broken photo never shifts the layout; failed loads show a muted placeholder icon.
 * `eager` is for photos in the first screen, so the largest content is not held back by lazy loading.
 */
export function ProductImage({
  src,
  alt,
  width,
  height,
  className,
  eager = false
}: {
  src: string;
  alt: string;
  width: number;
  height: number;
  className: string;
  eager?: boolean;
}) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  return (
    <div className={`shrink-0 overflow-hidden bg-muted ${className}`}>
      {failed ? (
        <span aria-hidden="true" className="grid h-full w-full place-items-center text-subink">
          <UtensilsCrossed size={40} strokeWidth={1.5} />
        </span>
      ) : (
        <img
          src={src}
          alt={alt}
          width={width}
          height={height}
          loading={eager ? "eager" : "lazy"}
          fetchPriority={eager ? "high" : "auto"}
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      )}
    </div>
  );
}
