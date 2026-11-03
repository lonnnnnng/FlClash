package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.ProfileDraftStore
import kotlinx.coroutines.CompletableDeferred

internal class PickerMemoryStore : ProfileDraftStore {
    var bytes: ByteArray? = null
    var readGate: CompletableDeferred<Unit>? = null
    override suspend fun read(): ByteArray? { readGate?.await(); return bytes?.copyOf() }
    override suspend fun write(content: ByteArray?) { bytes = content?.copyOf() }
}
