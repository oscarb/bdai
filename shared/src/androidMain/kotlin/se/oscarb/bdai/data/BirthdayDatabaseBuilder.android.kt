package se.oscarb.bdai.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun birthdayDatabaseBuilder(context: Context): RoomDatabase.Builder<BirthdayDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(BirthdayDatabase.DB_NAME)
    return Room.databaseBuilder<BirthdayDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}

fun createBirthdayDatabase(context: Context): BirthdayDatabase =
    createBirthdayDatabase(birthdayDatabaseBuilder(context))
