import type { Metadata, Viewport } from "next";
import { MenuCloser } from "@/components/MenuCloser";
import { SiteFooter } from "@/components/SiteFooter";
import { SYSTEM_THEME_COLORS, themeBootScript, themeCss } from "@/lib/themes";
import "./globals.css";

export const metadata: Metadata = {
  title: "MTG Trader",
  description: "Edit your MTG Trader wishlist from a computer.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: SYSTEM_THEME_COLORS,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // The boot script sets data-theme before React loads, hence suppressHydrationWarning.
    <html lang="en" suppressHydrationWarning>
      <head>
        <style id="themes" dangerouslySetInnerHTML={{ __html: themeCss() }} />
        <script dangerouslySetInnerHTML={{ __html: themeBootScript() }} />
      </head>
      <body>
        {children}
        <SiteFooter />
        <MenuCloser />
      </body>
    </html>
  );
}
