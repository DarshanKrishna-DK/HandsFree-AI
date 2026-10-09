package com.example.handsfree

import com.example.handsfree.agent.FoodIntake
import org.junit.Assert.*
import org.junit.Test

class FoodIntakeTest {
    @Test fun snacksNeverInventADish() {
        val intake = FoodIntake("I want to order some snacks for 5PM")
        assertEquals("food", intake.next()?.key)
        assertFalse(intake.constraints().joinToString().contains("dosa", ignoreCase = true))
        intake.answer("food", "Suggest something light")
        assertEquals("budget", intake.next()?.key)
        intake.answer("budget", "300 rupees total")
        intake.answer("diet", "vegetarian")
        intake.answer("quantity", "two people")
        assertTrue(intake.next()!!.text.contains("5PM"))
        intake.answer("timing", "Delivery today by 5 PM")
        assertNull(intake.next())
        assertTrue(intake.constraints().any { it.contains("300 rupees") })
    }
    @Test fun explicitPreferencesArePreserved() {
        val intake = FoodIntake("Order two masala dosas, vegetarian, budget Rs 300, now")
        assertNull(intake.next())
        assertTrue(intake.constraints().first().contains("two masala dosas"))
    }
    @Test fun lunchStillAsksWhatToEat() {
        assertEquals("food", FoodIntake("I need lunch").next()?.key)
        assertFalse(FoodIntake("I need lunch").answer("budget", "200"))
        assertFalse(FoodIntake("I need lunch").answer("food", " "))
    }
    @Test fun onlyActualInstalledFoodAppsAreCandidates() {
        assertEquals(emptyList<String>(), FoodIntake.appCandidates(listOf("com.example.maps")))
        assertEquals(listOf("in.swiggy.android"), FoodIntake.appCandidates(listOf("in.swiggy.android")))
        assertEquals(2, FoodIntake.appCandidates(listOf("in.swiggy.android", "com.application.zomato")).size)
        assertFalse(FoodIntake.applies("Open Calculator"))
        assertTrue(FoodIntake.applies("I need lunch"))
        assertTrue(FoodIntake.applies("Order two masala dosas"))
        assertTrue(FoodIntake.applies("Get samosas"))
        assertTrue(FoodIntake.applies("Find something in Swiggy"))
    }
    @Test fun oneAppOpensButSeveralOrMissingExplicitChoiceNeedAQuestion() {
        val zomato = "com.application.zomato"
        val swiggy = "in.swiggy.android"
        assertEquals(zomato, FoodIntake.automaticPackage("snacks for 5PM", listOf(zomato)))
        assertNull(FoodIntake.automaticPackage("snacks for 5PM", listOf(zomato, swiggy)))
        assertNull(FoodIntake.automaticPackage("Order lunch using Swiggy", listOf(zomato)))
        assertEquals(swiggy, FoodIntake.automaticPackage("Use Swiggy for lunch", listOf(zomato, swiggy)))
    }
}
