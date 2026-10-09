package com.example.handsfree
import com.example.handsfree.agent.*
import org.junit.Assert.*
import org.junit.Test
class CartReviewTest {
    private fun node(id: String, text: String, y: Int = 100) = ScreenElement(id, text, "", "Text", emptyList(), listOf(0, y, 300, y + 40))
    private val screen = ScreenObservation("s1", "com.application.zomato", listOf(node("cart", "Your cart"),
        node("item", "2 x Veg sandwich"), node("total", "Grand total ₹320.50"), node("pay", "Pay now")))
    private val args = ActionArguments(evidenceIds = listOf("item", "total"), cartItemIds = listOf("item"), cartTotalIds = listOf("total"))
    @Test fun readsExactDisplayedAmountAndItems() {
        val result = CartReview.from(screen, args)!!
        assertEquals("₹320.50", result.amount)
        assertEquals(listOf("2 x Veg sandwich"), result.items)
        assertTrue(result.handoff.contains("not placed an order"))
    }
    @Test fun itemSubtotalAndInventedIdsCannotComplete() {
        assertNull(CartReview.from(screen.copy(elements = screen.elements.map { if (it.id == "total") it.copy(text = "Item total ₹200") else it }), args))
        assertNull(CartReview.from(screen, args.copy(cartItemIds = listOf("invented"))))
        assertNull(CartReview.from(screen.copy(blockedReason = "Password"), args))
        assertNull(CartReview.from(screen.copy(packageName = "com.other"), args))
    }
    @Test fun menuPricesAndAmbiguousTotalsAreRejected() {
        assertNull(CartReview.from(screen.copy(elements = screen.elements.filter { it.id != "cart" }), args))
        assertNull(CartReview.from(screen.copy(elements = screen.elements + node("other", "To pay ₹400")), args))
        assertNull(CartReview.from(screen, args.copy(cartItemIds = emptyList())))
    }
    @Test fun acceptsAdjacentLabelButNotAnUnrelatedAmount() {
        val separate = screen.copy(elements = screen.elements.filter { it.id != "total" } + node("total", "To pay", 500) + node("amount", "₹1,200", 500))
        val proof = args.copy(evidenceIds = listOf("item", "total", "amount"), cartTotalIds = listOf("total", "amount"))
        assertEquals("₹1200", CartReview.from(separate, proof)!!.amount)
        assertNull(CartReview.from(separate.copy(elements = separate.elements.map { if (it.id == "amount") it.copy(bounds = listOf(0, 1500, 300, 1540)) else it }), proof))
        assertNull(CartReview.from(separate.copy(elements = separate.elements + node("another", "₹900", 500)), proof))
    }
    @Test fun headersUtilitiesAndItemsWithoutQuantityCannotProveCart() {
        assertNull(CartReview.from(screen, args.copy(evidenceIds = listOf("cart", "total"), cartItemIds = listOf("cart"))))
        for (text in listOf("Add cooking instructions", "Veg sandwich", "Qty: 2", "Quantity 2", "2 x"))
            assertNull(CartReview.from(screen.copy(elements = screen.elements.map { if (it.id == "item") it.copy(text = text) else it }), args))
    }
    @Test fun mergedSelectedTotalCannotHideADifferentSplitTotal() {
        val conflicting = screen.copy(elements = screen.elements + node("label2", "To pay", 800) + node("amount2", "₹350", 800))
        assertNull(CartReview.from(conflicting, args))
    }
    @Test fun malformedCurrencyCannotBeReadAsASmallerTotal() {
        assertNull(CartReview.from(screen.copy(elements = screen.elements.map { if (it.id == "total") it.copy(text = "Grand total ₹12,34") else it }), args))
    }
}
