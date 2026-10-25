package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticStore

class RoomRuntimeDiagnosticStore(
    private val dao: RuntimeDiagnosticDao,
) : RuntimeDiagnosticStore {
    override suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent> =
        dao.getRecent(limit)
            .asReversed()
            .mapNotNull(RuntimeDiagnosticEntity::toRuntimeDiagnosticEvent)

    override suspend fun append(event: RuntimeDiagnosticEvent, limit: Int) {
        dao.insertAndTrim(event.toRuntimeDiagnosticEntity(), limit)
    }

    override suspend fun clear() {
        dao.clear()
    }
}
