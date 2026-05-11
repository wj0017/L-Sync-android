package com.lsync.app.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.lsync.app.data.local.AppDatabase
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.FinanceRepository
import com.lsync.app.data.repository.TodoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lsync.db")
            .build()

    @Provides fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
    @Provides fun provideTodoDao(db: AppDatabase): TodoDao = db.todoDao()
    @Provides @Singleton fun provideTodoTemplateDao(db: AppDatabase): TodoTemplateDao = db.todoTemplateDao()
    @Provides fun provideFinanceDao(db: AppDatabase): FinanceDao = db.financeDao()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirestoreDataSource(firestore: FirebaseFirestore) =
        FirestoreDataSource(firestore)

    @Provides
    @Singleton
    fun provideEventRepository(dao: EventDao, remote: FirestoreDataSource) =
        EventRepository(dao, remote)

    @Provides
    @Singleton
    fun provideTodoRepository(
        todoDao: TodoDao,
        todoTemplateDao: TodoTemplateDao,
        financeDao: FinanceDao,
        remote: FirestoreDataSource,
    ) = TodoRepository(todoDao, todoTemplateDao, financeDao, remote)

    @Provides
    @Singleton
    fun provideFinanceRepository(dao: FinanceDao, remote: FirestoreDataSource) =
        FinanceRepository(dao, remote)
}
