package com.lsync.app.data.repository

import android.content.Context
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val financeDao: FinanceDao,
    private val todoDao: TodoDao,
    private val todoTemplateDao: TodoTemplateDao,
) {
    suspend fun migrate(newUserId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY, null) == newUserId) return  // 멱등성: 이미 마이그레이션됨

        financeDao.migrateUserId(LEGACY_USER_ID, newUserId)
        todoDao.migrateUserId(LEGACY_USER_ID, newUserId)
        todoTemplateDao.migrateUserId(LEGACY_USER_ID, newUserId)

        prefs.edit().putString(KEY, newUserId).apply()
    }

    companion object {
        private const val PREFS = "auth_migration"
        private const val KEY = "migrated_uid"
        private const val LEGACY_USER_ID = "local_user"
    }
}
