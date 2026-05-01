package com.example.autotoucher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.autotoucher.data.model.ActionType

@Database(
    entities = [TaskEntity::class, ActionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun actionDao(): ActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autotoucher.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/** Room TypeConverter：ActionType ↔ String（SQLite TEXT）。 */
class Converters {
    @TypeConverter
    fun fromActionType(type: ActionType): String = type.name

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.valueOf(value)
}
