package com.bqsqwad.share.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun db(@ApplicationContext context: Context): ShareDatabase =
        Room.databaseBuilder(context, ShareDatabase::class.java, "bq-sqwad-share.db").build()

    @Provides
    fun dao(db: ShareDatabase): TransferDao = db.transfers()
}
