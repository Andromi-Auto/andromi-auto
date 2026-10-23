package com.andrerinas.openheadunit.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndromiAutoStartTest {

    private val car = "AA:BB:CC:DD:EE:01"
    private val headphones = "AA:BB:CC:DD:EE:02"
    private val cars = setOf(car)

    @Test
    fun `the car disconnecting with automatic start on stops Android Auto`() {
        assertTrue(AndromiAutoStart.isCarLeaving(autoStartOn = true, carMacs = cars, mac = car, connected = false))
    }

    @Test
    fun `the car disconnecting with automatic start off leaves Android Auto alone`() {
        assertFalse(AndromiAutoStart.isCarLeaving(autoStartOn = false, carMacs = cars, mac = car, connected = false))
    }

    @Test
    fun `another device disconnecting is not the car leaving`() {
        assertFalse(AndromiAutoStart.isCarLeaving(autoStartOn = true, carMacs = cars, mac = headphones, connected = false))
        assertFalse(AndromiAutoStart.isCarLeaving(autoStartOn = true, carMacs = cars, mac = null, connected = false))
    }

    @Test
    fun `the car connecting is not the car leaving`() {
        assertFalse(AndromiAutoStart.isCarLeaving(autoStartOn = true, carMacs = cars, mac = car, connected = true))
    }

    @Test
    fun `no chosen car means nothing is the car`() {
        assertFalse(AndromiAutoStart.isCarLeaving(autoStartOn = true, carMacs = emptySet(), mac = car, connected = false))
        assertFalse(AndromiAutoStart.isCarBack(emptySet(), car, connected = true))
    }

    @Test
    fun `only the car connecting counts as it coming back`() {
        assertTrue(AndromiAutoStart.isCarBack(cars, car, connected = true))
        assertFalse(AndromiAutoStart.isCarBack(cars, car, connected = false))
        assertFalse(AndromiAutoStart.isCarBack(cars, headphones, connected = true))
    }
}
