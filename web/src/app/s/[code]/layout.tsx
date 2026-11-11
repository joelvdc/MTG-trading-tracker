import type { Metadata } from "next";

// Shared pages are for whoever got the link: search engines are asked not to list them or follow their links.
export const metadata: Metadata = {
  title: "Shared from MTG Trader",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function SharedLayout({ children }: { children: React.ReactNode }) {
  return children;
}
