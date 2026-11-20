package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgtrader.container
import com.mtgtrader.data.Orders

/**
 * "Ordered": the remembered orders that hold this printing in this finish (see Orders.hits), one line each,
 * tap to open the order. Nothing when there are none. Since 1.103.
 */
@Composable
fun OrderedLines(scryfallId: String, foil: Boolean, onOpen: (orderId: Long) -> Unit) {
    val c = LocalContext.current.container
    val orders by remember { c.db.orderDao().observeAll() }.collectAsStateWithLifecycle(null)
    val hits = remember(orders, scryfallId, foil) { orders?.let { Orders.hits(it, scryfallId, foil) }.orEmpty() }
    if (hits.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("Ordered", style = MaterialTheme.typography.titleSmall)
        hits.forEach { hit ->
            val o = hit.order
            val text = listOfNotNull(
                orderDate(o.date),
                if (o.shop == Orders.PRECON) "Precon: ${o.title}" else o.title,
                "${hit.quantity}×",
                hit.price?.let { "${Fmt.money(it)} each" },
                "finish unknown".takeIf { !hit.finishKnown },
            ).joinToString(" · ") + "  ›"
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(o.id) }.padding(vertical = 6.dp),
            )
        }
    }
}
