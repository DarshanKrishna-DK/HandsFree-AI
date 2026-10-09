package com.example.handsfree.agent

/** User preferences only. No restaurant/menu data or assumed dishes live here. */
class FoodIntake(val original: String) {
    companion object {
        val foodPackages = setOf("com.application.zomato", "in.swiggy.android")
        private const val dishes = "pizzas?|dosas?|sandwich(?:es)?|biryani|idlis?|samosas?|pasta|salads?|burgers?|noodles|chapati|roti|rice|soup|poha|upma|parathas?"
        fun applies(goal: String): Boolean = Regex(
            "(?i)\\b(food|snacks?|lunch|dinner|breakfast|meals?|hungry|restaurants?|takeaway|$dishes)\\b|ಊಟ|ತಿಂಡಿ|ಆಹಾರ"
        ).containsMatchIn(goal) || (Regex("(?i)\\b(zomato|swiggy)\\b").containsMatchIn(goal) &&
            Regex("(?i)\\b(order|eat|find|search)\\b").containsMatchIn(goal))
        fun appCandidates(packages: List<String>) = packages.filter { it in foodPackages }.distinct()
        fun automaticPackage(goal: String, installed: List<String>): String? {
            val requested = mapOf("zomato" to "com.application.zomato", "swiggy" to "in.swiggy.android")
                .filterKeys { Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(goal) }.values
            return if (requested.isNotEmpty()) requested.singleOrNull()?.takeIf { it in installed }
            else appCandidates(installed).singleOrNull()
        }
    }
    data class Question(val key: String, val text: String)
    private val answers = linkedMapOf<String, String>()
    private val questions = buildList {
        // When uncertain, ask. A general meal name must never become a specific dish.
        val explicitDish = Regex("(?i)\\b($dishes)\\b").containsMatchIn(original)
        if (!explicitDish) add(Question("food", "What would you like to eat? Name something you enjoy, or ask me to suggest options."))
        if (!Regex("(?i)(₹|\\b(?:rs\\.?|inr|budget)\\b|no (?:spending )?limit)").containsMatchIn(original))
            add(Question("budget", "What is your total budget? You can also say no budget limit."))
        if (!Regex("(?i)\\b(vegetarian|non.?veg(?:etarian)?|veg|vegan|allerg\\w*|no dietary restrictions)\\b|ಸಸ್ಯಾಹಾರಿ").containsMatchIn(original))
            add(Question("diet", "Any dietary preferences or allergies? For example, vegetarian, or no restrictions."))
        if (!Regex("(?i)\\b(?:for\\s+)?(?:[1-9]\\d*|one|two|three|four|five|six)\\s+(?:[a-z]+\\s+){0,2}(?:people|persons?|servings?|portions?|dosas?|pizzas?|sandwiches|samosas?)\\b").containsMatchIn(original))
            add(Question("quantity", "How many people or portions should I look for?"))
        val time = Regex("(?i)\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b").find(original)?.value
        if (time != null) add(Question("timing", "You mentioned $time. Do you want delivery by then or to place the order then, and on which day?"))
        else if (!Regex("(?i)\\b(now|asap|right away|within \\d+ minutes|today|tomorrow)\\b").containsMatchIn(original))
            add(Question("timing", "When would you like the food? Say now, or tell me a delivery time and day."))
    }
    fun next(): Question? = questions.firstOrNull { it.key !in answers }
    fun answer(key: String, text: String): Boolean {
        if (next()?.key != key || text.isBlank()) return false
        answers[key] = text.trim().take(1000)
        return true
    }
    fun constraints(): List<String> = listOf("Food request, exactly as the user wrote it: $original") +
        answers.map { (key, value) -> "User's $key preference: $value" } + listOf(
            "Do not assume a dish, restaurant, budget or quantity beyond the user's words. Ask when unclear.",
            "Compare actual observed options. Ask the user to choose before adding food. Stop before checkout.",
            "A requested time is not a scheduled order. Verify delivery estimates/scheduling in the app; never claim future execution is arranged.")
}
