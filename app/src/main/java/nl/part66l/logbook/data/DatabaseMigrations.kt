package nl.part66l.logbook.data

import androidx.room.migration.Migration

/**
 * Every schema change from version 12 onward, in order. Room throws at startup if the
 * installed database's version needs a migration that isn't listed here — that crash is
 * intentional (see [nl.part66l.logbook.di.DatabaseModule]'s doc comment): it means a schema
 * change shipped without one, which would otherwise silently drop the device's data.
 *
 * Writing one: bump `version` in [AppDatabase], run `:app:compileDebugKotlin` once so Room
 * exports the new `app/schemas/.../<n>.json`, diff it against `<n-1>.json` for the exact
 * column/table/index changes, add a `Migration(n - 1, n) { ... }` below using real `ALTER
 * TABLE`/`CREATE TABLE` SQL (Room does not infer migrations from the entity classes), and add
 * it to [ALL_MIGRATIONS].
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf()
