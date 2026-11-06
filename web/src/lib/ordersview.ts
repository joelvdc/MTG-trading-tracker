// The orders page's data (server side): the order list with today's worth, in the app's currency.
import type { CkFile } from "./cardkingdom";
import type { DetailsFile } from "./carddetails";
import { setupFor } from "./collview";
import { currency, rate } from "./money";
import { orderRows, type OrdersView } from "./orders";
import { PRICE_TYPE_LABELS, priceType, type PriceFile } from "./prices";
import { canWrite, prefs, type SyncFile } from "./syncfile";

export function ordersView(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): OrdersView {
  const p = prefs(file);
  const cur = currency(p.currency);
  return {
    orders: orderRows(file, setupFor(file, prices, details, ck)),
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    priceTypeLabel: PRICE_TYPE_LABELS[priceType(p.priceType)],
    readOnly: !canWrite(file),
  };
}
