package com.mtgtrader

import com.mtgtrader.ui.backImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackFaceTest {
    @Test
    fun backPictureSitsNextToTheFront() {
        assertEquals(
            "https://cards.scryfall.io/large/back/1/3/13a1b4d1.jpg?1",
            backImageUrl("https://cards.scryfall.io/large/front/1/3/13a1b4d1.jpg?1"),
        )
        assertNull(backImageUrl(null))
        assertNull(backImageUrl("https://example.com/card.jpg"))
    }
}
