package it.airgap.beaconsdk.blockchain.substrate.data

import fromValues
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.assertEquals

internal class SubstrateNetworkTest {
    @Test
    fun `is deserialized from JSON`() {
        listOf(
            expectedWithJsonStrings(),
            expectedWithJsonStrings(includeNulls = true),
            expectedWithJsonStrings(name = "name"),
            expectedWithJsonStrings(rpcUrl = "rpcUrl"),
            expectedWithJsonStrings(name = "name", rpcUrl = "rpcUrl"),
        ).map {
            Json.decodeFromString<SubstrateNetwork>(it.second) to it.first
        }.forEach {
            assertEquals(it.second, it.first)
        }
    }

    @Test
    fun `serializes to JSON`() {
        listOf(
            expectedWithJsonStrings(),
            expectedWithJsonStrings(name = "name"),
            expectedWithJsonStrings(rpcUrl = "rpcUrl"),
            expectedWithJsonStrings(name = "name", rpcUrl = "rpcUrl"),
        ).map {
            Json.decodeFromString(JsonObject.serializer(), Json.encodeToString(it.first)) to
                    Json.decodeFromString(JsonObject.serializer(), it.second)
        }.forEach {
            assertEquals(it.second, it.first)
        }
    }

    private fun expectedWithJsonStrings(
        genesisHash: String = "genesisHash",
        name: String? = null,
        rpcUrl: String? = null,
        includeNulls: Boolean = false,
    ): Pair<SubstrateNetwork, String> {
        val values = mapOf(
            "genesisHash" to genesisHash,
            "name" to name,
            "rpcUrl" to rpcUrl,
        )

        val json = JsonObject.fromValues(values, includeNulls).toString()

        return SubstrateNetwork(genesisHash, name, rpcUrl) to json
    }
}