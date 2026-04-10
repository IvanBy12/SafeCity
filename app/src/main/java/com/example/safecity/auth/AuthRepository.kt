package com.example.safecity.auth

import com.example.safecity.network.TokenStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser get() = auth.currentUser

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        // forceRefresh = true para garantizar que el nuevo token
        // se cachee con su tiempo de expiración correcto
        TokenStore.refresh(forceRefresh = true)
    }

    suspend fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        TokenStore.refresh(forceRefresh = true)
    }

    fun logout() {
        auth.signOut()
        TokenStore.clear()
    }
}