import type { Metadata, Viewport } from "next";
import { MenuCloser } from "@/components/MenuCloser";
import { SiteFooter } from "@/components/SiteFooter";
import "./globals.css";

export const metadata: Metadata = {
  title: "MTG Trader",
  description: "Edit your MTG Trader wishlist from a computer.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#15131A",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {children}
        <SiteFooter />
        <MenuCloser />
      </body>
    </html>
  );
}
