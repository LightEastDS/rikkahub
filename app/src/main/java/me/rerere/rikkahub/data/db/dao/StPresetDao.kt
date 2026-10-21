package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.StPresetEntity
import me.rerere.rikkahub.data.db.entity.StVariableEntity

@Dao
interface StPresetDao {
    @Query("SELECT * FROM st_presets ORDER BY sort_index ASC, imported_at ASC")
    fun observeAll(): Flow<List<StPresetEntity>>

    @Query("SELECT * FROM st_presets WHERE id = :id")
    suspend fun getById(id: String): StPresetEntity?

    @Query("SELECT COALESCE(MAX(sort_index), 0) FROM st_presets")
    suspend fun maxSortIndex(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: StPresetEntity)

    @Query("UPDATE st_presets SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE st_presets SET order_json = :orderJson WHERE id = :id")
    suspend fun setOrderJson(id: String, orderJson: String)

    @Query("UPDATE st_presets SET raw_json = :rawJson WHERE id = :id")
    suspend fun updateRawJson(id: String, rawJson: String)

    @Query("UPDATE st_presets SET raw_json = :rawJson, order_json = '' WHERE id = :id")
    suspend fun resetToOriginal(id: String, rawJson: String)

    @Query("DELETE FROM st_presets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface StVariableDao {
    @Query("SELECT * FROM st_variables WHERE scope_id = :scopeId ORDER BY name ASC")
    suspend fun getScope(scopeId: String): List<StVariableEntity>

    @Query("DELETE FROM st_variables WHERE scope_id = :scopeId")
    suspend fun deleteScope(scopeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<StVariableEntity>)
}
