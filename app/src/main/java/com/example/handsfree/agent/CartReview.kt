package com.example.handsfree.agent

/** Reads displayed cart evidence only. It never computes a payable amount from item prices. */
object CartReview {
    data class Summary(val items: List<String>, val amount: String, val totalText: String) {
        val spokenReview get() = "The cart shows ${items.joinToString("; ")}. The displayed total is $amount. Are these the items and quantities you want?"
        val handoff get() = "Your cart is ready for your review. The app displays $amount to pay. Check the final amount in the app and complete payment yourself. I have not placed an order. Microphone access is ending for payment."
    }
    private val money = Regex("(?i)(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9,.])")
    private val finalLabel = Regex("(?i)\\b(grand total|to pay|amount payable|total payable|total amount|bill total|payable amount|net payable)\\b")
    private val excluded = Regex("(?i)\\b(subtotal|sub total|item total|savings|discount|delivery fee|taxes)\\b")
    private fun label(e: ScreenElement) = listOf(e.text.trim(), e.description.trim()).filter { it.isNotEmpty() }.distinct().joinToString(" ")
    private fun isTotal(value: String) = !excluded.containsMatchIn(value) &&
        (finalLabel.containsMatchIn(value) || Regex("(?i)^total(?:\\s*[:：]?\\s*(?:₹|Rs|INR).*)?$").matches(value.trim()))
    private fun adjacent(a: ScreenElement, b: ScreenElement): Boolean {
        val anchor = a.bounds; val bounds = b.bounds
        return bounds.size == 4 && anchor.size == 4 &&
            (kotlin.math.abs(bounds[1] - anchor[1]) <= 60 ||
                (bounds[1] >= anchor[3] && bounds[1] - anchor[3] <= 120 && kotlin.math.abs(bounds[0] - anchor[0]) <= 80))
    }
    fun from(screen: ScreenObservation, args: ActionArguments): Summary? {
        if (screen.blockedReason != null || screen.packageName !in FoodIntake.foodPackages) return null
        if (args.cartItemIds.isEmpty() || args.cartTotalIds.isEmpty() || args.cartTotalIds.size > 3) return null
        val byId = screen.elements.associateBy { it.id }
        if (byId.size != screen.elements.size) return null
        if ((args.cartItemIds + args.cartTotalIds).any { it !in args.evidenceIds || it !in byId }) return null
        if (args.cartItemIds.any { it in args.cartTotalIds }) return null
        val labels = screen.elements.map(::label)
        if (labels.none { Regex("(?i)^(?:your |my )?cart(?:\\s.*)?$|\\bbill (?:details|summary)\\b|\\border summary\\b|^your order$").containsMatchIn(it) }) return null
        val totalElements = args.cartTotalIds.distinct().map { byId.getValue(it) }
        if (totalElements.none { isTotal(label(it)) }) return null
        val totalText = totalElements.joinToString(" ", transform = ::label)
        val amounts = money.findAll(totalText).map { it.groupValues[1].replace(",", "").toBigDecimal() }.toList()
        if (amounts.size != 1) return null
        // A separate amount must be next to its label, not an unrelated price elsewhere.
        if (totalElements.size > 1) {
            val anchor = totalElements.first { isTotal(label(it)) }
            if (totalElements.any { !adjacent(anchor, it) }) return null
            val nearby = screen.elements.filter { adjacent(anchor, it) }
                .flatMap { money.findAll(label(it)).map { m -> m.groupValues[1].replace(",", "").toBigDecimal() }.toList() }
            if (nearby.isEmpty() || nearby.any { it.compareTo(amounts.single()) != 0 }) return null
        }
        // Resolve every visible total label, including split labels omitted by the model.
        for (anchor in screen.elements.filter { isTotal(label(it)) }) {
            val embedded = money.findAll(label(anchor)).map { it.groupValues[1].replace(",", "").toBigDecimal() }.toList()
            val candidates = embedded.ifEmpty { screen.elements.filter { adjacent(anchor, it) }
                .flatMap { money.findAll(label(it)).map { m -> m.groupValues[1].replace(",", "").toBigDecimal() }.toList() } }
            if (candidates.isEmpty() || candidates.any { it.compareTo(amounts.single()) != 0 }) return null
        }
        val items = args.cartItemIds.distinct().map { label(byId.getValue(it)) }
        val utility = Regex("(?i)\\b(cart|instructions|cutlery|coupon|bill|checkout|delivery address|payment|savings|discount)\\b|^(?:add|continue|your order|order summary)$")
        if (items.none { it.any(Char::isLetter) } || items.any { it.isBlank() || SafetyPolicyEngine.isProtected(it) || isTotal(it) || utility.containsMatchIn(it) }) return null
        val inlineQuantity = Regex("(?i)(?:\\b[1-9]\\d*\\s*[x×]\\s*\\p{L}|\\b(?:qty|quantity)\\s*[:：]?\\s*[1-9]\\d*\\b|\\b[x×]\\s*[1-9]\\d*\\b)")
        val itemElements = args.cartItemIds.distinct().map { byId.getValue(it) }
        val quantityOnly = Regex("(?i)^(?:(?:qty|quantity)\\s*[:：]?\\s*)?[1-9]\\d*\\s*(?:[x×])?$")
        val names = itemElements.filter { label(it).any(Char::isLetter) && !quantityOnly.matches(label(it)) }
        if (names.isEmpty()) return null
        if (names.any { item -> !inlineQuantity.containsMatchIn(label(item)) && itemElements.none {
                    quantityOnly.matches(label(it)) && adjacent(item, it)
                } }) return null
        return Summary(items, "₹" + amounts.single().toPlainString(), totalText)
    }
}
