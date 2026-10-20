package com.examcamp.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class AuthUser(val uid: String, val name: String?, val email: String?, val photoUrl: String?)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    /** [fresh] is true right after an explicit sign-in tap, false when a saved session is restored. */
    data class SignedIn(val user: AuthUser, val fresh: Boolean) : AuthState
}

/** Firebase Auth with Google sign-in through Credential Manager (the native replacement for signInWithPopup). */
class AuthManager(private val appContext: Context) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    private var freshRequested = false

    val firebaseReady: Boolean get() = FirebaseApp.getApps(appContext).isNotEmpty()

    init {
        if (firebaseReady) {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user == null) {
                    freshRequested = false
                    _state.value = AuthState.SignedOut
                } else {
                    _state.value = AuthState.SignedIn(user.toAuthUser(), freshRequested)
                    freshRequested = false
                }
            }
        } else {
            _state.value = AuthState.SignedOut
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid, displayName, email, photoUrl?.toString())

    private fun webClientId(): String? {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) null else appContext.getString(id)
    }

    /** Returns success, a cancelled result (message == null) or a failure with a readable message. */
    suspend fun signIn(activity: Activity): Result<Unit> {
        if (!firebaseReady) {
            return Result.failure(IllegalStateException("Firebase is not configured. Add app/google-services.json and rebuild."))
        }
        val clientId = webClientId()
            ?: return Result.failure(IllegalStateException("Google sign-in is not set up in google-services.json (missing web client)."))
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                freshRequested = true
                FirebaseAuth.getInstance()
                    .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                    .await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(CancelledException())
        } catch (e: Exception) {
            freshRequested = false
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        if (firebaseReady) FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
    }

    /**
     * Deletes the signed-in account: [serverDelete] erases the cloud rows (it gets a fresh ID token), then the
     * Firebase user is deleted. Firebase wants a recent sign-in for that; if it says so, the person picks their
     * Google account once more. Safe to retry after a failure.
     */
    suspend fun deleteAccount(activity: Activity, serverDelete: suspend (idToken: String) -> Unit): Result<Unit> {
        if (!firebaseReady) return Result.failure(IllegalStateException("Firebase is not configured."))
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            val token = user.getIdToken(true).await().token ?: throw IllegalStateException("No ID token.")
            serverDelete(token)
            try {
                user.delete().await()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                reauthenticate(activity, user)
                user.delete().await()
            }
            try {
                CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(CancelledException())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun reauthenticate(activity: Activity, user: FirebaseUser) {
        val clientId = webClientId() ?: throw IllegalStateException("Google sign-in is not set up (missing web client).")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            user.reauthenticate(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        } else {
            throw IllegalStateException("Unexpected credential type.")
        }
    }

    class CancelledException : Exception("cancelled")
}
