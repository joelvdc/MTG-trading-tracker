import { redirect } from "next/navigation";

/** The wishlist now sits in the collection (its "Wishlist" chip), as in the app. */
export default function WishlistPage() {
  redirect("/collection?binder=wishlist");
}
