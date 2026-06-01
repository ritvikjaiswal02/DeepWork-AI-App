package com.example.deepworkai.network

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class GoogleAuthManager(context: Context) {

    private val clientId = com.example.deepworkai.BuildConfig.GOOGLE_CLIENT_ID

    init {
        Log.d("GoogleAuthManager", "Initializing with Client ID: $clientId")
    }

    private val gso = if (clientId.isNotBlank()) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(clientId)
            .build()
    } else {
        // No client ID configured — Google Sign-In disabled, email/password login still works
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }

    private val googleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getSignInIntent() = googleSignInClient.signInIntent
}
