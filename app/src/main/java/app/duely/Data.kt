package app.duely

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class Freq { ONCE, WEEKLY, MONTHLY, YEARLY }

@Entity
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val title: String,
    val note: String?,
    val type: Freq,
    val startDate: String,
    val anchorDay: Int,
    val occurrenceCount: Int?,
    val icon: String,
    val color: Int
)

@Entity(indices = [Index(value = ["groupId", "period"], unique = true)])
data class OccurrenceState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val period: String,
    val title: String,
    val note: String?,
    val dueDate: String,
    val completedAt: String?,
    val deleted: Boolean,
    val isException: Boolean,
    val icon: String,
    val color: Int
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM Reminder")
    fun reminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM OccurrenceState")
    fun states(): Flow<List<OccurrenceState>>

    @Query("SELECT * FROM Reminder")
    suspend fun remindersNow(): List<Reminder>

    @Query("SELECT * FROM OccurrenceState")
    suspend fun statesNow(): List<OccurrenceState>

    @Query("SELECT * FROM Reminder WHERE groupId = :g")
    suspend fun group(g: String): List<Reminder>

    @Insert
    suspend fun insert(r: Reminder): Long

    @Update
    suspend fun update(r: Reminder)

    @Delete
    suspend fun delete(r: Reminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: OccurrenceState)

    @Delete
    suspend fun deleteState(s: OccurrenceState)

    @Query("DELETE FROM OccurrenceState WHERE groupId = :g AND period >= :p AND completedAt IS NULL")
    suspend fun clearFuture(g: String, p: String)
}

@Database(entities = [Reminder::class, OccurrenceState::class], version = 3, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): ReminderDao

    companion object {
        @Volatile private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, Db::class.java, "duely.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
