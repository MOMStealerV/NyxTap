package com.example

import com.example.data.provider.AhemMailProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AhemMailProviderTest {

    @Test
    fun testAhemMailProviderStructure() = runBlocking {
        val provider = AhemMailProvider()
        assertTrue(provider.providerName.contains("AHEM"))
        val generated = provider.generateMailbox()
        assertNotNull(generated)
        assertTrue(generated.contains("@"))
        assertTrue(generated.substringBefore("@").isNotBlank())
        assertTrue(generated.substringAfter("@").isNotBlank())
    }
}
