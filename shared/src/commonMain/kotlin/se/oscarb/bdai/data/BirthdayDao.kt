package se.oscarb.bdai.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BirthdayDao {

    @Query("SELECT * FROM birthday_storage WHERE id = 1")
    fun observeStorage(): Flow<BirthdayStorageEntity?>

    @Query("SELECT * FROM birthday_storage WHERE id = 1")
    suspend fun getStorage(): BirthdayStorageEntity?

    @Upsert
    suspend fun upsertStorage(entity: BirthdayStorageEntity)
}
