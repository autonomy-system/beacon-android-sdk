package it.airgap.beaconsdk.core.data

import fromValues
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import it.airgap.beaconsdk.core.internal.blockchain.BlockchainRegistry
import it.airgap.beaconsdk.core.internal.blockchain.MockBlockchain
import it.airgap.beaconsdk.core.internal.di.DependencyRegistry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mockBeaconSdk
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class AppMetadataTest {

    @MockK
    private lateinit var dependencyRegistry: DependencyRegistry

    @MockK
    private lateinit var blockchainRegistry: BlockchainRegistry

    private lateinit var mockBlockchain: MockBlockchain

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockBeaconSdk(dependencyRegistry = dependencyRegistry)

        mockBlockchain = MockBlockchain()

        every { dependencyRegistry.blockchainRegistry } returns blockchainRegistry

        every { blockchainRegistry.get(any()) } returns mockBlockchain
        every { blockchainRegistry.getOrNull(any()) } returns mockBlockchain
    }

    @Test
    fun `is deserialized from JSON`() {
        listOf(
            expectedWithJson(),
            expectedWithJson(icon = "icon"),
            expectedWithJson(includeNulls = true),
        ).map {
            Json.decodeFromString<AppMetadata>(it.second) to it.first
        }.forEach {
            assertEquals(it.second, it.first)
        }
    }

    @Test
    fun `serializes to JSON`() {
        listOf(expectedWithJson(), expectedWithJson(icon = "icon"))
            .map { Json.decodeFromString(JsonObject.serializer(), Json.encodeToString(it.first)) to
                    Json.decodeFromString(JsonObject.serializer(), it.second) }
            .forEach {
                assertEquals(it.second, it.first)
            }
    }

    private fun expectedWithJson(
        blockchainIdentifier: String = MockBlockchain.IDENTIFIER,
        senderId: String = "senderId",
        name: String = "name",
        icon: String? = null,
        includeNulls: Boolean = false
    ): Pair<AppMetadata, String> {
        val values = mapOf(
            "blockchainIdentifier" to blockchainIdentifier,
            "senderId" to senderId,
            "name" to name,
            "icon" to icon,
        )

        val json = JsonObject.fromValues(values, includeNulls).toString()

        return MockAppMetadata(senderId, name, icon) to json
    }
}