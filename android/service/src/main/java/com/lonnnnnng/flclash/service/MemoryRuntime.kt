package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.core.CoreMemoryStats

interface MemoryRuntime : CoreRuntimeStatusProvider {
    suspend fun memoryStats(): CoreMemoryStats
    suspend fun releaseMemory(): CoreMemoryStats
}
