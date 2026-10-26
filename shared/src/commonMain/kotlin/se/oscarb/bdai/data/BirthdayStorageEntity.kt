package se.oscarb.bdai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "birthday_storage")
data class BirthdayStorageEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val entriesJson: String,
    val settingsJson: String
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
