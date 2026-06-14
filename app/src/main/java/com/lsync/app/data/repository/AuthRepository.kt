package com.lsync.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.lsync.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val migrationHelper: AuthMigrationHelper,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    val authState: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, firebaseAuth.currentUser)

    init {
        // 앱 재실행 시 자동 로그인: 이미 currentUser가 있으면 마이그레이션 시도 (멱등성으로 안전)
        firebaseAuth.currentUser?.uid?.let { uid ->
            scope.launch {
                migrationHelper.migrate(uid)
                // 마이그레이션 직후 원격 복원 pull (UI는 Room Flow로 자동 반영)
                syncRepository.pullAll(uid)
            }
        }
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(credential).await().user
            ?: throw IllegalStateException("signIn succeeded but user is null")
        // 신규 로그인: 마이그레이션 동기 완료 후 반환 → NavGraph 전환 시 데이터 준비됨
        migrationHelper.migrate(user.uid)
        // 마이그레이션 직후 원격 복원 pull은 백그라운드로 (로그인 흐름 차단 방지, UI는 Room Flow로 반영)
        scope.launch { syncRepository.pullAll(user.uid) }
        return user
    }

    suspend fun signOut() { firebaseAuth.signOut() }
}
