package ro.cosminmihu.ktor.monitor.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.cosminmihu.ktor.monitor.db.sqldelight.Call
import ro.cosminmihu.ktor.monitor.domain.model.buildShellCommand

internal class ExportCallRequestAsCurlUseCase {

    suspend operator fun invoke(call: Call): String = withContext(Dispatchers.Default) {
        buildShellCommand(
            call = call,
            tool = "curl",
            method = { """-X "$it"""" },
            header = { """-H "$it"""" },
            data = { """--data-binary "$it"""" },
        )
    }
}
