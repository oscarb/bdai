package se.oscarb.bdai.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [BirthdayStorageEntity::class],
    version = 1,
    exportSchema = false
)
@ConstructedBy(BirthdayDatabaseConstructor::class)
abstract class BirthdayDatabase : RoomDatabase() {
    abstract val birthdayDao: BirthdayDao

    companion object {
        const val DB_NAME = "bdai_birthdays.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object BirthdayDatabaseConstructor : RoomDatabaseConstructor<BirthdayDatabase> {
    override fun initialize(): BirthdayDatabase
}

fun createBirthdayDatabase(builder: RoomDatabase.Builder<BirthdayDatabase>): BirthdayDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
