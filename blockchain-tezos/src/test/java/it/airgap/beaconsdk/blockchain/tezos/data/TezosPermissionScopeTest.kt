package it.airgap.beaconsdk.blockchain.tezos.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Test
import kotlin.test.assertEquals

internal class TezosPermissionScopeTest {

    @Test
    fun `is deserialized from string`() {
        expectedWithJsonValues()
            .map { Json.decodeFromString<TezosPermission.Scope>(it.second) to it.first }
            .forEach { assertEquals(it.second, it.first) }
    }

    @Test
    fun `serializes to string`() {
        expectedWithJsonValues()
            .map { Json.encodeToString(it.first) to it.second }
            .forEach { assertEquals(it.second, it.first) }
    }

    @Test
    fun `is deserialized from list of strings`() {
        expectedWithJsonArray()
            .map { Json.decodeFromString<List<TezosPermission.Scope>>(it.second) to it.first }
            .forEach { assertEquals(it.second, it.first) }
    }

    @Test
    fun `serializes to list of string`() {
        expectedWithJsonArray()
            .map { Json.decodeFromString(JsonArray.serializer(), Json.encodeToString(it.first)) to
                    Json.decodeFromString(JsonArray.serializer(), it.second) }
            .forEach {
                assertEquals(it.second, it.first)
            }
    }

    private fun expectedWithJsonValues(): List<Pair<TezosPermission.Scope, String>> = listOf(
        TezosPermission.Scope.Sign to "\"sign\"",
        TezosPermission.Scope.OperationRequest to "\"operation_request\"",
    )

    private fun expectedWithJsonArray(): List<Pair<List<TezosPermission.Scope>, String>> = listOf(
        listOf(TezosPermission.Scope.Sign, TezosPermission.Scope.OperationRequest) to
                """
                    [
                        "sign", 
                        "operation_request" 
                    ]
                """.trimIndent(),
    )
}