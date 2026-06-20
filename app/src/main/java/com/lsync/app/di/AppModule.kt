package com.lsync.app.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lsync.app.data.local.AppDatabase
import com.lsync.app.data.local.BibleDatabase
import com.lsync.app.data.local.EsvDatabase
import com.lsync.app.data.local.dao.BibleDao
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.dao.EsvDao
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.MemoDao
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.notification.AlarmScheduler
import com.lsync.app.data.repository.BudgetRepository
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.FinanceRepository
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.data.repository.SearchRepository
import com.lsync.app.data.repository.SyncRepository
import com.lsync.app.data.repository.TodoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lsync.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

    @Provides fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
    @Provides fun provideTodoDao(db: AppDatabase): TodoDao = db.todoDao()
    @Provides @Singleton fun provideTodoTemplateDao(db: AppDatabase): TodoTemplateDao = db.todoTemplateDao()
    @Provides fun provideFinanceDao(db: AppDatabase): FinanceDao = db.financeDao()
    @Provides fun provideReadingPlanDao(db: AppDatabase): ReadingPlanDao = db.readingPlanDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    @Singleton
    fun provideMemoDao(db: AppDatabase): MemoDao = db.memoDao()

    @Provides
    @Singleton
    fun provideReadingPlanRepository(
        dao: ReadingPlanDao,
        @ApplicationContext context: Context,
    ) = ReadingPlanRepository(dao, context)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirestoreDataSource(firestore: FirebaseFirestore) =
        FirestoreDataSource(firestore)

    @Provides
    @Singleton
    fun provideEventRepository(
        dao: EventDao,
        remote: FirestoreDataSource,
        alarmScheduler: AlarmScheduler,
    ) = EventRepository(dao, remote, alarmScheduler)

    @Provides
    @Singleton
    fun provideTodoRepository(
        todoDao: TodoDao,
        todoTemplateDao: TodoTemplateDao,
        financeDao: FinanceDao,
        remote: FirestoreDataSource,
        alarmScheduler: AlarmScheduler,
    ) = TodoRepository(todoDao, todoTemplateDao, financeDao, remote, alarmScheduler)

    @Provides
    @Singleton
    fun provideFinanceRepository(
        dao: FinanceDao,
        remote: FirestoreDataSource,
        authRepository: com.lsync.app.data.repository.AuthRepository,
    ) = FinanceRepository(dao, remote, authRepository)

    @Provides
    @Singleton
    fun provideBudgetRepository(
        dao: BudgetDao,
        remote: FirestoreDataSource,
    ) = BudgetRepository(dao, remote)

    @Provides
    @Singleton
    fun provideSearchRepository(
        eventDao: EventDao,
        todoDao: TodoDao,
        financeDao: FinanceDao,
    ) = SearchRepository(eventDao, todoDao, financeDao)

    @Provides
    @Singleton
    fun provideSyncRepository(
        remote: FirestoreDataSource,
        eventDao: EventDao,
        todoDao: TodoDao,
        financeDao: FinanceDao,
        budgetDao: BudgetDao,
    ) = SyncRepository(remote, eventDao, todoDao, financeDao, budgetDao)

    @Provides
    @Singleton
    fun provideBibleDatabase(@ApplicationContext context: Context): BibleDatabase {
        // 콘텐츠 버전이 올라가면 기존 DB 삭제 → asset 재복사
        val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
        val CONTENT_VERSION = 2  // 개역개정4판 완전본 (TinyText 잘림 해결)
        if (prefs.getInt("bible_content_version", 0) < CONTENT_VERSION) {
            context.getDatabasePath("bible_v3.db").also { f ->
                f.delete()
                java.io.File("${f.absolutePath}-wal").delete()
                java.io.File("${f.absolutePath}-shm").delete()
            }
            prefs.edit().putInt("bible_content_version", CONTENT_VERSION).apply()
        }
        return Room.databaseBuilder(context, BibleDatabase::class.java, "bible_v3.db")
            .createFromAsset("bible.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideBibleDao(db: BibleDatabase): BibleDao = db.bibleDao()

    @Provides
    @Singleton
    fun provideEsvDatabase(@ApplicationContext context: Context): EsvDatabase =
        Room.databaseBuilder(context, EsvDatabase::class.java, "esv.db")
            .createFromAsset("esv.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideEsvDao(db: EsvDatabase): EsvDao = db.esvDao()
}
