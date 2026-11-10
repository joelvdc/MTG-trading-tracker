/** The MTG Trader "Card Swap" logo (same drawing as the app icon and src/app/icon.svg). Decorative: the name is always written next to it. */
export function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg className="logo" width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" focusable="false">
      <rect width="100" height="100" rx="22" fill="#1F1A2E" />
      <g transform="rotate(10 58 44)">
        <rect x="43" y="23" width="30" height="42" rx="4" fill="#7E57C2" />
      </g>
      <g transform="rotate(-10 42 56)">
        <rect x="27" y="35" width="30" height="42" rx="4" fill="#E0A526" />
        <rect x="31" y="39" width="22" height="16" rx="1.5" fill="#1F1A2E" />
      </g>
      <g fill="none" stroke="#FFFFFF" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M80 58 A31 31 0 0 1 62 80" />
        <path d="M68 82 L62 80 L64 74" />
        <path d="M20 42 A31 31 0 0 1 38 20" />
        <path d="M32 18 L38 20 L36 26" />
      </g>
    </svg>
  );
}
