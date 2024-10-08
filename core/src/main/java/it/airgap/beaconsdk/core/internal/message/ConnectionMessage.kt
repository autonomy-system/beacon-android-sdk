package it.airgap.beaconsdk.core.internal.message

import androidx.annotation.RestrictTo
import it.airgap.beaconsdk.core.data.Origin

internal typealias ConnectionTransportMessage = ConnectionMessage<*>

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed interface ConnectionMessage<T> {
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val origin: Origin

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val content: T

    public companion object {}
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class SerializedConnectionMessage(
    override val origin: Origin,
    override val content: String,
) : ConnectionMessage<String> {
    public constructor(pair: Pair<Origin, String>) : this(pair.first, pair.second)

    public companion object {}
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class BeaconConnectionMessage(
    override val origin: Origin,
    override val content: VersionedBeaconMessage,
) : ConnectionMessage<VersionedBeaconMessage> {
    public constructor(pair: Pair<Origin, VersionedBeaconMessage>) : this(pair.first, pair.second)

    public companion object {}
}