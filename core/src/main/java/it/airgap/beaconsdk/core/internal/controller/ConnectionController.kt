package it.airgap.beaconsdk.core.internal.controller

import androidx.annotation.RestrictTo
import it.airgap.beaconsdk.core.data.Connection
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.exception.ConnectionException
import it.airgap.beaconsdk.core.exception.MultipleConnectionException
import it.airgap.beaconsdk.core.internal.message.BeaconConnectionMessage
import it.airgap.beaconsdk.core.internal.message.ConnectionTransportMessage
import it.airgap.beaconsdk.core.internal.message.SerializedConnectionMessage
import it.airgap.beaconsdk.core.internal.serializer.Serializer
import it.airgap.beaconsdk.core.internal.transport.Transport
import it.airgap.beaconsdk.core.internal.utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@OptIn(ExperimentalCoroutinesApi::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ConnectionController internal constructor(private val transports: List<Transport>, private val serializer: Serializer) {

    public fun subscribe(): Flow<Result<BeaconConnectionMessage>> =
        transports
            .map { it.subscribe() }
            .merge()
            .map { BeaconConnectionMessage.fromResult(it)}

    public suspend fun send(message: BeaconConnectionMessage): Result<Unit> =
        runCatchingFlat {
            val serializedContent = serializer.serialize(message.content).getOrThrow()
            val serializedMessage = SerializedConnectionMessage(message.origin, serializedContent)

            return transports
                .asyncMap { it.send(serializedMessage) }
                .foldIndexed(Result.success()) { index, acc, next ->
                    acc.concat(next, transports[index].type)
                }
        }

    private fun BeaconConnectionMessage.Companion.fromResult(
        connectionMessage: Result<ConnectionTransportMessage>
    ): Result<BeaconConnectionMessage> = connectionMessage.flatMap { message ->
        val content = when (message) {
            is SerializedConnectionMessage -> serializer.deserialize(message.content)
            is BeaconConnectionMessage -> Result.success(message.content)
        }

        content.map { BeaconConnectionMessage(message.origin, it) }
    }

    public fun startOpenChannelListener(): Flow<Result<P2pPeer>> = transports
        .map { transport ->
            transport.startOpenChannelListener()
        }
        .merge()

    public fun getRelayServers(): List<String> = transports
        .map {
            it.getRelayServers()
        }
        .flatten()
        .toList()

    private fun Result<Unit>.concat(other: Result<Unit>, connectionType: Connection.Type): Result<Unit> {
        onSuccess {
            other.onSuccess { return Result.success() }
            other.onFailure { otherException -> return Result.failure(ConnectionException.from(connectionType, otherException)) }
        }
        onFailure { thisException ->
            other.onSuccess { return Result.failure(ConnectionException.from(connectionType, thisException)) }
            other.onFailure { otherException ->
                val concat = thisException.concat(ConnectionException.from(connectionType, otherException))
                concat?.let { Result.failure<Unit>(concat) } ?: Result.failure()
            }
        }

        return Result.failure()
    }

    private fun Throwable.concat(other: Throwable): Throwable? =
        when {
            this is MultipleConnectionException && other is MultipleConnectionException ->
                MultipleConnectionException(errors + other.errors.distinctBy { it.type })

            this is MultipleConnectionException && other is ConnectionException ->
                MultipleConnectionException(errors + other)

            this is ConnectionException && other is ConnectionException ->
                MultipleConnectionException(listOf(this, other))

            this is ConnectionException && other is MultipleConnectionException ->
                MultipleConnectionException(other.errors + this)

            else -> null
        }
}