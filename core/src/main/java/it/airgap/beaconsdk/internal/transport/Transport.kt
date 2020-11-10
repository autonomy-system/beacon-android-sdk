package it.airgap.beaconsdk.internal.transport

import it.airgap.beaconsdk.internal.message.ConnectionTransportMessage
import it.airgap.beaconsdk.internal.utils.InternalResult
import it.airgap.beaconsdk.internal.utils.logDebug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

internal abstract class Transport {
    abstract val type: Type

    protected abstract val connectionMessages: Flow<InternalResult<ConnectionTransportMessage>>

    protected abstract suspend fun sendMessage(message: String, recipient: String?): InternalResult<Unit>

    fun subscribe(): Flow<InternalResult<ConnectionTransportMessage>> =
        connectionMessages.onStart { logDebug("$TAG $type", "subscribed") }

    suspend fun send(message: String, recipient: String? = null): InternalResult<Unit> {
        logDebug("$TAG $type", "sending $message to $recipient")
        return sendMessage(message, recipient)
    }

    companion object {
        const val TAG = "Transport"
    }

    enum class Type {
        P2P
    }
}