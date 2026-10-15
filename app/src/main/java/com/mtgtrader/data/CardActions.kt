package com.mtgtrader.data

/** How long received wishlist cards are kept before the app clears them; never unless chosen. Since 1.38. */
enum class ReceivedClear(val key: String, val label: String, val days: Int?) {
    NEVER("never", "Never (keep them until you clear them)", null),
    MONTH("1m", "After 1 month", 30),
    THREE_MONTHS("3m", "After 3 months", 91),
    SIX_MONTHS("6m", "After 6 months", 182),
    YEAR("1y", "After 1 year", 365);

    /** Received cards from before this are cleared; null keeps them all. */
    fun cutoff(now: Long): Long? = days?.let { now - it * 24L * 3600 * 1000 }

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/** What swiping a card in the collection does (left and right are chosen in Settings). Since 1.38. */
enum class SwipeAction(val key: String, val label: String, val short: String) {
    MOVE_TO_BINDER("move", "Move to binder…", "Move"),
    ADD_TO_TRADE("trade", "Add to a trade…", "Trade"),
    TRADE_BINDER("tradeBinder", "Into or out of the trade binder", "Trade binder"),
    ONE_MORE("plus", "One copy more", "+1"),
    ONE_LESS("minus", "One copy less", "−1"),
    ADD_TO_WISHLIST("wish", "Add to the wishlist", "Wishlist"),
    NOTHING("none", "Nothing", "");

    companion object {
        val DEFAULT_RIGHT = MOVE_TO_BINDER
        val DEFAULT_LEFT = ADD_TO_TRADE
        fun fromKey(key: String?, default: SwipeAction) = entries.firstOrNull { it.key == key } ?: default
    }
}
