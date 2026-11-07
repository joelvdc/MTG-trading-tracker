"use client";

import { useState } from "react";
import { backImageUrl, hasBackFace } from "@/lib/decklayout";

/**
 * A card's picture with a Flip button under it when the card has a back face (a double-faced card). If the back
 * picture turns out not to exist (the type line only hinted at two faces), the front stays and the button goes.
 */
export function FlipPicture({
  url,
  className,
  alt = "",
  layout,
  typeLine,
  startFlipped = false,
}: {
  url: string;
  className: string;
  alt?: string;
  layout?: string | null;
  typeLine?: string | null;
  startFlipped?: boolean;
}) {
  const [back, setBack] = useState(startFlipped && hasBackFace(layout, typeLine));
  const [noBack, setNoBack] = useState(false);
  const canFlip = hasBackFace(layout, typeLine) && !noBack;
  return (
    <div className="flip-picture">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={back ? backImageUrl(url) : url}
        alt={alt}
        className={className}
        onError={() => {
          if (back) {
            setBack(false);
            setNoBack(true);
          }
        }}
      />
      {canFlip && (
        <button type="button" className="btn small-btn" onClick={() => setBack((b) => !b)}>
          ⟳ Flip
        </button>
      )}
    </div>
  );
}
