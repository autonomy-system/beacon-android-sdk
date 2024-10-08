package it.airgap.beaconsdk.transport.p2p.matrix.internal

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.internal.crypto.Crypto
import it.airgap.beaconsdk.core.internal.crypto.data.KeyPair
import it.airgap.beaconsdk.core.internal.data.BeaconApplication
import it.airgap.beaconsdk.core.internal.transport.p2p.data.P2pPairingResponse
import it.airgap.beaconsdk.core.internal.utils.asHexString
import it.airgap.beaconsdk.core.internal.utils.toHexString
import it.airgap.beaconsdk.transport.p2p.matrix.internal.matrix.data.MatrixEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class P2pMatrixCommunicatorTest {

    @MockK
    private lateinit var crypto: Crypto

    private lateinit var p2pMatrixCommunicator: P2pMatrixCommunicator

    private val app: BeaconApplication = BeaconApplication(
        KeyPair(byteArrayOf(0), byteArrayOf(0)),
        "mockApp",
        "mockAppIcon",
        "mockAppUrl",
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { crypto.hashKey(any<ByteArray>()) } answers { Result.success(firstArg()) }

        p2pMatrixCommunicator = P2pMatrixCommunicator(app, crypto)
    }

    @After
    fun cleanUp() {
        unmockkAll()
    }

    @Test
    fun `creates recipient id`() {
        val publicKey = "e40476d7".asHexString().toByteArray()
        val relayServer = "relayServer"

        val recipientIdentifier = p2pMatrixCommunicator.recipientIdentifier(publicKey, relayServer).getOrThrow()

        assertEquals("@${publicKey.toHexString().asString()}:$relayServer", recipientIdentifier.asString())
    }

    @Test
    fun `checks if message is coming from specified sender`() {
        val node = "node"

        val expectedSender = "e40476d7".asHexString().toByteArray()
        val unexpectedSender = "e40476d8".asHexString().toByteArray()

        val expectedIdentifier = p2pMatrixCommunicator.recipientIdentifier(expectedSender, "relayServer").getOrThrow()
        val unexpectedIdentifier = p2pMatrixCommunicator.recipientIdentifier(unexpectedSender, "relayServer").getOrThrow()

        val fromExpected = MatrixEvent.TextMessage(node, "roomId", expectedIdentifier.asString(), "message")
        val fromUnexpected = MatrixEvent.TextMessage(node, "roomId", unexpectedIdentifier.asString(), "message")

        assertTrue(
            p2pMatrixCommunicator.isMessageFrom(fromExpected, expectedSender),
            "Expected message to be recognized as coming from the specified sender",
        )
        assertFalse(
            p2pMatrixCommunicator.isMessageFrom(fromUnexpected, expectedSender),
            "Expected message to be recognized as not coming from the specified sender",
        )
    }

    @Test
    fun `checks if message is valid`() {
        val node = "node"

        val valid = MatrixEvent.TextMessage(node, "roomId", "sender", "validMessage")
        val invalid = MatrixEvent.TextMessage(node, "roomId", "sender", "invalidMessage")
        
        every { crypto.validateEncryptedMessage(valid.message) } returns true
        every { crypto.validateEncryptedMessage(invalid.message) } returns false

        assertTrue(p2pMatrixCommunicator.isValidMessage(valid), "Expected message to be recognized as valid")
        assertFalse(p2pMatrixCommunicator.isValidMessage(invalid), "Expected message to be recognized as invalid")
    }

    @Test
    fun `creates pairing request payload for specified version`() {
        val id = "id"
        val type = "p2p-pairing-response"
        val relayServer = "relayServer"

        versionsWithPairingPayloads(id, type, app.name, app.icon, app.url, app.keyPair.publicKey.toHexString().asString(), relayServer)
            .map { P2pPeer(id = id, name = "name", publicKey = "01", relayServer = "peerServer", version = it.first) to it.second }
            .map { p2pMatrixCommunicator.pairingPayload(it.first, relayServer) to it.second }
            .forEach { assertEquals(it.second, it.first.getOrThrow()) }
    }

    @Test
    fun `creates channel opening message`() {
        val recipient = "recipient"
        val payload = "payload"

        val message = p2pMatrixCommunicator.channelOpeningMessage(recipient, payload)

        assertEquals("@channel-open:$recipient:$payload", message)
    }

    private fun versionsWithPairingPayloads(
        id: String = "id",
        type: String = "type",
        appName: String = "mockApp",
        appIcon: String? = null,
        appUrl: String? = null,
        publicKey: String = "00",
        relayServer: String = "relayServer",
    ): List<Pair<String, String>> = listOf(
        "1" to publicKey,
        "1.0" to publicKey,
        "1.0.0" to publicKey,
        "1.abc" to publicKey,
        "2" to Json.encodeToString(P2pPairingResponse(id, type, appName, "2", publicKey, relayServer, appIcon, appUrl)),
        "2.0" to Json.encodeToString(P2pPairingResponse(id, type, appName, "2.0", publicKey, relayServer, appIcon, appUrl)),
        "2.0.0" to Json.encodeToString(P2pPairingResponse(id, type, appName, "2.0.0", publicKey, relayServer, appIcon, appUrl)),
        "2.abc" to Json.encodeToString(P2pPairingResponse(id, type, appName, "2.abc", publicKey, relayServer, appIcon, appUrl)),
        "3" to Json.encodeToString(P2pPairingResponse(id, type, appName, "3", publicKey, relayServer, appIcon, appUrl)),
    )
}