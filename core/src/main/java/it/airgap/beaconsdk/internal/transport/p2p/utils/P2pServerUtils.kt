package it.airgap.beaconsdk.internal.transport.p2p.utils

import it.airgap.beaconsdk.internal.crypto.Crypto
import it.airgap.beaconsdk.internal.utils.HexString
import it.airgap.beaconsdk.internal.utils.asHexString
import java.math.BigInteger

internal class P2pServerUtils(private val crypto: Crypto, private val matrixNodes: List<String>) {

    fun getRelayServer(publicKey: ByteArray, nonce: HexString? = null): String {
        return getRelayServer(publicKey.asHexString(), nonce)
    }

    fun getRelayServer(publicKey: HexString, nonce: HexString? = null): String {
        val hash = crypto.hashKey(publicKey).value().asHexString()
        val nonceValue = nonce?.value() ?: ""

        return matrixNodes.minByOrNull {
            val relayServerHash = crypto.hash(it + nonceValue, 32).value().asHexString()
            hash.absDiff(relayServerHash)
        } ?: ""
    }

    private fun HexString.absDiff(other: HexString): BigInteger =
        (toBigInteger() - other.toBigInteger()).abs()
}