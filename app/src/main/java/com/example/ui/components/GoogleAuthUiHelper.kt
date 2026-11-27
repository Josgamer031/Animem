package com.example.ui.components

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser?) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

object GoogleAuthUiHelper {
    private const val TAG = "GoogleAuthUiHelper"

    suspend fun signInInteractive(
        context: Context,
        credentialManager: CredentialManager
    ): GoogleSignInOutcome {
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(cred.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken,
                    null
                )
                val authRes = Firebase.auth.signInWithCredential(firebaseCredential).await()
                GoogleSignInOutcome.Success(authRes.user ?: Firebase.auth.currentUser)
            } else {
                GoogleSignInOutcome.Error("Tipo de credencial no compatible con Google.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled by user or credential config", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "CredentialManager error during Google sign-in", e)
            GoogleSignInOutcome.Error(
                e.localizedMessage ?: "No se pudo iniciar sesión con Google. Verifica tu cuenta."
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Google sign-in failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Error al autenticar con Firebase.")
        }
    }

    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            Firebase.auth.signOut()
            runCatching {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}
