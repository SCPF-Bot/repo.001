package com.mlbb.assistant.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for the MLBB Assistant app.
 *
 * Schema export is enabled (see ksp { room.schemaLocation } in build.gradle.kts).
 * The generated JSON files in /schemas/ should be committed to version control
 * to enable safe migration authoring in future versions.
 *
 * Construction is exclusively via [com.mlbb.assistant.di.DatabaseModule] which
 * applies MIGRATION_1_2 correctly.
 *
 * NOTE: The companion object factory that previously lived here was removed.
 * It bypassed the Migration(1, 2) defined in DatabaseModule by calling
 * fallbackToDestructiveMigration without migration objects, creating two
 * divergent construction paths. The DI module is the single source of truth
 * for database construction.
 */
@Database(
    entities = [
        HeroEntity::class,
        DraftSessionEntity::class,
        HeroPoolEntity::class,
        CounterLookupEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heroDao(): HeroDao
    abstract fun draftSessionDao(): DraftSessionDao
    abstract fun heroPoolDao(): HeroPoolDao
    abstract fun counterLookupDao(): CounterLookupDao
}
