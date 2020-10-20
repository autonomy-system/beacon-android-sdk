package it.airgap.beaconsdk.internal.transport.p2p

import it.airgap.beaconsdk.internal.BeaconConfig
import it.airgap.beaconsdk.internal.crypto.Crypto
import it.airgap.beaconsdk.internal.crypto.data.KeyPair
import it.airgap.beaconsdk.internal.crypto.data.SessionKeyPair
import it.airgap.beaconsdk.internal.matrix.MatrixClient
import it.airgap.beaconsdk.internal.matrix.data.client.MatrixClientEvent
import it.airgap.beaconsdk.internal.transport.data.TransportMessage
import it.airgap.beaconsdk.internal.utils.*
import it.airgap.beaconsdk.internal.utils.HexString
import it.airgap.beaconsdk.internal.utils.InternalResult
import it.airgap.beaconsdk.internal.utils.asHexString
import it.airgap.beaconsdk.internal.utils.launch
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger

internal class P2pCommunicationClient(
    private val name: String,
    private val matrixClients: List<MatrixClient>,
    private val matrixNodes: List<String>,
    private val replicationCount: Int,
    private val crypto: Crypto,
    private val keyPair: KeyPair
) {
    private val messageFlows: MutableMap<HexString, Flow<InternalResult<String>>> = mutableMapOf()
    private val keyPairs: MutableMap<HexString, SessionKeyPair> = mutableMapOf()

    private val handshakeInfo: HandshakeInfo
        get() = HandshakeInfo(
            name,
            BeaconConfig.versionName,
            keyPair.publicKey.asHexString().value(withPrefix = false),
            getRelayServer()
        )

    private val handshakeInfoJson: String
        get() = Json.encodeToString(handshakeInfo)

    fun subscribeTo(publicKey: HexString): Flow<InternalResult<TransportMessage>> =
        messageFlows.getOrPut(publicKey) {
            matrixClients.map(MatrixClient::events)
                .merge() // @ExperimentalCoroutinesApi
                .logAndIgnoreErrors()
                .filterIsInstance<MatrixClientEvent.Message.Text>()
                .filter { it.isTextMessageFrom(publicKey) }
                .map { decrypt(publicKey, it) }
        }.map { TransportMessage.fromInternalResult(publicKey, it) }

    suspend fun sendTo(publicKey: HexString, message: String): InternalResult<Unit> =
        suspendTryInternal {
            val encrypted = encrypt(publicKey, message).getOrThrow()
            for (i in 0 until replicationCount) {
                val recipientHash = crypto.hashKey(publicKey).getOrThrow().asHexString()
                val relayServer = getRelayServer(recipientHash, i.asHexString())
                val recipient = getRecipientIdentifier(recipientHash, relayServer)

                matrixClients.launch {
                    val room = getRelevantRoom(it, recipient)
                    it.sendTextMessage(room, encrypted)
                }
            }
        }

    suspend fun sendPairingRequest(
        publicKey: HexString,
        relayServer: String
    ): InternalResult<Unit> =
        suspendTryInternal {
            val recipientHash = crypto.hashKey(publicKey).getOrThrow().asHexString()
            val recipient = getRecipientIdentifier(recipientHash, relayServer)

            val encrypted = crypto.encryptMessageWithPublicKey(handshakeInfoJson, publicKey.asByteArray()).getOrThrow()
            matrixClients.launch {
                val room = getRelevantRoom(it, recipient)
                it.sendTextMessage(room, "@channel-open:$recipient:$encrypted")
            }
        }

    private fun encrypt(publicKey: HexString, message: String): InternalResult<String> =
        keyPairs.getOrCreate(publicKey)
            .flatMap { crypto.encryptMessageWithSharedKey(message, it.tx) }

    private fun decrypt(
        publicKey: HexString,
        textMessage: MatrixClientEvent.Message.Text
    ): InternalResult<String> =
        keyPairs.getOrCreate(publicKey)
            .flatMap {
                crypto.decryptMessageWithSharedKey(
                    textMessage.content.message.content,
                    it.rx
                )
            }

    private fun getRelayServer(recipientHash: HexString? = null, nonce: HexString? = null): String {
        val hash = recipientHash ?: crypto.hashKey(keyPair.publicKey).getOrThrow().asHexString()
        val nonceValue = nonce?.value(withPrefix = false) ?: ""

        return matrixNodes.minByOrNull {
            val relayServerHash = crypto.hash(it + nonceValue, 32).getOrThrow().asHexString()
            hash.absDiff(relayServerHash)
        } ?: ""
    }

    private fun getRecipientIdentifier(recipientHash: HexString, relayServer: String): String =
        "@${recipientHash.value(withPrefix = false)}:$relayServer"

    // TODO: types && implementation
    private fun getRelevantRoom(client: MatrixClient, recipient: String): Any {
        return Any()
    }

    private fun <T> Flow<InternalResult<T>>.logAndIgnoreErrors(): Flow<T> = mapNotNull {
        it.getOrLogError(TAG)
    }

    private fun MatrixClientEvent<*>.isTextMessageFrom(publicKey: HexString): Boolean {
        val keyHash = crypto.hashKey(publicKey).getOrLogError(TAG)

        return this is MatrixClientEvent.Message.Text
                && content.message.sender.startsWith("@${keyHash?.asHexString()?.value(withPrefix = false)}")
                && crypto.validateEncryptedMessage(content.message.content)
    }

    private fun MutableMap<HexString, SessionKeyPair>.getOrCreate(publicKey: HexString): InternalResult<SessionKeyPair> =
        if (containsKey(publicKey)) internalSuccess(getValue(publicKey))
        else crypto.createServerSessionKeyPair(publicKey.asByteArray(), keyPair.privateKey)
            .alsoIfSuccess { put(publicKey, it) }

    private fun TransportMessage.Companion.fromInternalResult(
        publicKey: HexString,
        message: InternalResult<String>
    ): InternalResult<TransportMessage> =
        message.map { TransportMessage(publicKey.value(withPrefix = false), it) }

    private fun HexString.absDiff(other: HexString): BigInteger =
        (toBigInteger() - other.toBigInteger()).abs()

    companion object {
        const val TAG = "P2pCommunicationClient"
    }

    @Serializable
    data class HandshakeInfo(
        val name: String,
        val version: String,
        val publicKey: String,
        val relayServer: String
    )
}