package ro.cosminmihu.ktor.monitor.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal typealias Headers = ImmutableMap<String, ImmutableList<String>>

internal fun Map<String, List<String>>.toImmutableHeaders(): Headers =
    mapValues { it.value.toImmutableList() }.toImmutableMap()
