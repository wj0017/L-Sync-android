package com.lsync.app.data.alarm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes alarm mutations without blocking synchronous scheduler entry points. */
@Singleton
class AlarmCommandQueue @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED)

    private data class Command(val run: suspend () -> Unit, val done: CompletableDeferred<Unit>?)

    init {
        scope.launch {
            for (command in commands) {
                try {
                    command.run()
                    command.done?.complete(Unit)
                } catch (failure: Exception) {
                    Log.e("AlarmCommandQueue", "Alarm command failed", failure)
                    command.done?.completeExceptionally(failure)
                }
            }
        }
    }

    /** Capture an alarm key, then re-read current Room state inside [command]. */
    fun submit(command: suspend () -> Unit) {
        check(commands.trySend(Command(command, null)).isSuccess) { "Alarm command queue unavailable" }
    }

    /** Critical rearm/cancel callers await this from goAsync or a suspend repository method. */
    suspend fun submitAndAwait(command: suspend () -> Unit) {
        val done = CompletableDeferred<Unit>()
        check(commands.trySend(Command(command, done)).isSuccess) { "Alarm command queue unavailable" }
        done.await()
    }
}
