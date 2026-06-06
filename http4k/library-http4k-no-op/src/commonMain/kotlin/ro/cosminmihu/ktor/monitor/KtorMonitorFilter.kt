package ro.cosminmihu.ktor.monitor

import org.http4k.core.Filter
import org.http4k.core.HttpHandler

/**
 * No-op implementation.
 */
public class KtorMonitorFilter() : Filter {

    public constructor(block: KtorMonitorFilterConfig.() -> Unit) : this()

    override fun invoke(next: HttpHandler): HttpHandler = next
}

