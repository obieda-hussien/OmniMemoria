package com.omnimemoria.data.repository

import com.omnimemoria.data.local.db.SortPreset
import com.omnimemoria.data.local.db.SortPresetDao
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SortPresetRepository @Inject constructor(
    private val sortPresetDao: SortPresetDao
) {
    suspend fun seedDefaultsIfEmpty() {
        if (sortPresetDao.count() == 0) {
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
    }

    fun getCurrentSort(): Flow<SortConfig> {
        return sortPresetDao.getDefault().map { preset ->
            preset?.toSortConfig() ?: SortConfig()
        }
    }

    fun getAll(): Flow<List<SortPreset>> = sortPresetDao.getAll()

    suspend fun insert(preset: SortPreset) = sortPresetDao.insert(preset)

    suspend fun delete(id: Int) = sortPresetDao.delete(id)

    suspend fun setDefault(id: Int) = sortPresetDao.setDefault(id)

    private fun SortPreset.toSortConfig(): SortConfig {
        val sortBy = runCatching { SortBy.valueOf(this.sortBy) }.getOrDefault(SortBy.DATE_TAKEN)
        val sortOrder = runCatching { SortOrder.valueOf(this.sortOrder) }.getOrDefault(SortOrder.DESCENDING)
        val groupBy = this.groupBy?.let { runCatching { GroupBy.valueOf(it) }.getOrNull() }
        return SortConfig(sortBy = sortBy, sortOrder = sortOrder, groupBy = groupBy)
    }
}
