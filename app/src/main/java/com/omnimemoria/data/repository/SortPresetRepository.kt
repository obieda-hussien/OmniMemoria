package com.omnimemoria.data.repository

import androidx.room.withTransaction
import com.omnimemoria.data.local.db.AppDatabase
import com.omnimemoria.data.local.db.SortPreset
import com.omnimemoria.data.local.db.SortPresetDao
import com.omnimemoria.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SortPresetRepository @Inject constructor(
    private val sortPresetDao: SortPresetDao,
    private val database: AppDatabase
) {
    private val mutex = Mutex()

    fun getCurrentPreset(): Flow<SortPreset> = flow {
        ensureDefaultsSeeded()
        emitAll(sortPresetDao.getDefault().map { it ?: makePreset("Custom", SortConfig(), FilterConfig(), true) })
    }.distinctUntilChanged()

    fun getCurrentSort(): Flow<SortConfig> = getCurrentPreset().map { it.toSortConfig() }.distinctUntilChanged()

    fun getAll(): Flow<List<SortPreset>> = flow {
        ensureDefaultsSeeded()
        emitAll(sortPresetDao.getAll())
    }

    suspend fun save(name: String, config: SortConfig, filter: FilterConfig) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 60 && trimmed != ACTIVE_NAME) { "Use a name between 1 and 60 characters" }
        mutex.withLock { database.withTransaction { sortPresetDao.insert(makePreset(trimmed, config, filter, false)) } }
    }

    suspend fun delete(id: Int) = mutex.withLock {
        database.withTransaction {
            val preset = sortPresetDao.findById(id) ?: return@withTransaction
            // The active working preset is private; deleting a named preset must not reset the gallery.
            if (!preset.isDefault) sortPresetDao.delete(id)
        }
    }

    suspend fun updateActive(config: SortConfig, filter: FilterConfig = FilterConfig()) = mutex.withLock {
        database.withTransaction {
            seedIfEmpty()
            val active = sortPresetDao.getDefault().first()
            if (active != null && active.name == ACTIVE_NAME) {
                sortPresetDao.insert(makePreset(ACTIVE_NAME, config, filter, true).copy(id = active.id))
            } else {
                sortPresetDao.clearDefault()
                sortPresetDao.insert(makePreset(ACTIVE_NAME, config, filter, true))
            }
        }
    }

    private suspend fun ensureDefaultsSeeded() = mutex.withLock {
        database.withTransaction { seedIfEmpty() }
    }

    private suspend fun seedIfEmpty() {
        if (sortPresetDao.count() != 0) return
        sortPresetDao.insert(makePreset("Newest First", SortConfig(), FilterConfig(), true))
        sortPresetDao.insert(makePreset("Largest Size", SortConfig(sortBy = SortBy.SIZE), FilterConfig(), false))
        sortPresetDao.insert(makePreset("Name A-Z", SortConfig(sortBy = SortBy.NAME, sortOrder = SortOrder.ASCENDING), FilterConfig(), false))
    }

    private fun makePreset(name: String, config: SortConfig, filter: FilterConfig, active: Boolean) = SortPreset(
        name = name, sortBy = config.sortBy.name, sortOrder = config.sortOrder.name,
        groupBy = config.groupBy?.name, isDefault = active, filterConfig = FilterPresetCodec.encode(filter)
    )

    companion object {
        private const val ACTIVE_NAME = "__active_query__"
    }
}

fun SortPreset.toSortConfig() = SortConfig(
    sortBy = runCatching { SortBy.valueOf(sortBy) }.getOrDefault(SortBy.DATE_TAKEN),
    sortOrder = runCatching { SortOrder.valueOf(sortOrder) }.getOrDefault(SortOrder.DESCENDING),
    groupBy = groupBy?.let { runCatching { GroupBy.valueOf(it) }.getOrNull() }
)

fun SortPreset.toFilterConfig(): FilterConfig = FilterPresetCodec.decode(filterConfig)
