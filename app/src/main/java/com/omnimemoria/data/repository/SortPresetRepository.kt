package com.omnimemoria.data.repository

import com.omnimemoria.data.local.db.SortPreset
import com.omnimemoria.data.local.db.SortPresetDao
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SortPresetRepository @Inject constructor(
    private val sortPresetDao: SortPresetDao
) {
    private companion object {
        const val LAST_USED_PRESET_NAME = "Last Used"
    }

    private val seedMutex = Mutex()

    fun getCurrentSort(): Flow<SortConfig> = flow {
        ensureDefaultsSeeded()
        emitAll(
            sortPresetDao.getDefault().map { preset ->
                preset?.toSortConfig() ?: SortConfig()
            }
        )
    }

    fun getAll(): Flow<List<SortPreset>> = flow {
        ensureDefaultsSeeded()
        emitAll(sortPresetDao.getAll())
    }

    suspend fun insert(preset: SortPreset) = sortPresetDao.insert(preset)

    suspend fun delete(id: Int) = sortPresetDao.delete(id)

    suspend fun setDefault(id: Int) = sortPresetDao.setDefault(id)

    suspend fun saveLastUsed(config: SortConfig) {
        ensureDefaultsSeeded()
        seedMutex.withLock {
            sortPresetDao.clearDefault()
            val existingId = sortPresetDao.getByName(LAST_USED_PRESET_NAME)?.id ?: 0
            val lastUsedPreset = SortPreset(
                id = existingId,
                name = LAST_USED_PRESET_NAME,
                sortBy = config.sortBy.name,
                sortOrder = config.sortOrder.name,
                groupBy = config.groupBy?.name,
                isDefault = true
            )
            sortPresetDao.insert(lastUsedPreset)
        }
    }

    private suspend fun ensureDefaultsSeeded() {
        seedMutex.withLock {
            if (sortPresetDao.count() == 0) {
                seedDefaults()
            }
        }
    }

    private suspend fun seedDefaults() {
        sortPresetDao.insert(
            SortPreset(
                name = "Newest First",
                sortBy = SortBy.DATE_TAKEN.name,
                sortOrder = SortOrder.DESCENDING.name,
                groupBy = null,
                isDefault = true
            )
        )
        sortPresetDao.insert(
            SortPreset(
                name = "Largest Size",
                sortBy = SortBy.SIZE.name,
                sortOrder = SortOrder.DESCENDING.name,
                groupBy = null,
                isDefault = false
            )
        )
        sortPresetDao.insert(
            SortPreset(
                name = "Name A-Z",
                sortBy = SortBy.NAME.name,
                sortOrder = SortOrder.ASCENDING.name,
                groupBy = null,
                isDefault = false
            )
        )
    }

    private fun SortPreset.toSortConfig(): SortConfig {
        val sortBy = runCatching { SortBy.valueOf(this.sortBy) }.getOrDefault(SortBy.DATE_TAKEN)
        val sortOrder = runCatching { SortOrder.valueOf(this.sortOrder) }.getOrDefault(SortOrder.DESCENDING)
        val groupBy = this.groupBy?.let { runCatching { GroupBy.valueOf(it) }.getOrNull() }
        return SortConfig(sortBy = sortBy, sortOrder = sortOrder, groupBy = groupBy)
    }
}
