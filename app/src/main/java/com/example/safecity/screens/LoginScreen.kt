package com.example.safecity.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.safecity.auth.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch



@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onGoRegister: () -> Unit,
    onLoggedIn: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    // Mensajes locales (para reset y Google)
    var localMsg by remember { mutableStateOf<String?>(null) }
    var localErr by remember { mutableStateOf<String?>(null) }
    var googleLoading by remember { mutableStateOf(false) }
    var resetLoading by remember { mutableStateOf(false) }

    fun clearLocalMessages() {
        localMsg = null
        localErr = null
    }

    // ---------- Google Sign-In Launcher ----------
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleLoading = false

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)

            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                localErr = "No se obtuvo idToken. Revisa default_web_client_id."
                return@rememberLauncherForActivityResult
            }

            googleLoading = true
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { t ->
                    googleLoading = false
                    if (t.isSuccessful) {
                        scope.launch {
                            com.example.safecity.network.TokenStore.refresh(forceRefresh = true)
                            onLoggedIn()
                        }
                    } else {
                        localErr = t.exception?.message ?: "Error autenticando con Google"
                    }
                }
        } catch (e: ApiException) {
            localErr = "Google Sign-In error: ${e.statusCode}"
        } catch (e: Exception) {
            localErr = e.message ?: "Error con Google Sign-In"
        }
    }

    fun startGoogleSignIn() {
        clearLocalMessages()
        googleLoading = true

        val clientId = getDefaultWebClientId(context)
        if (clientId.isBlank()) {
            googleLoading = false
            localErr = "No se encontró default_web_client_id. Revisa google-services.json."
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(context, gso)
        client.signOut()
        googleLauncher.launch(client.signInIntent)
    }

    fun sendResetEmail() {
        clearLocalMessages()
        val e = email.trim()

        if (e.isBlank()) {
            localErr = "Escribe tu correo para enviarte el enlace de recuperación."
            return
        }

        resetLoading = true
        auth.sendPasswordResetEmail(e)
            .addOnCompleteListener { task ->
                resetLoading = false
                if (task.isSuccessful) {
                    localMsg = "Te enviamos un enlace de recuperación a: $e"
                } else {
                    localErr = task.exception?.message ?: "No se pudo enviar el correo de recuperación"
                }
            }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(padding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column {
                            Text(
                                text = "Iniciar sesión",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Accede a SafeCity para continuar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider()

                    // ✅ Google Login
                    OutlinedButton(
                        onClick = { startGoogleSignIn() },
                        enabled = !googleLoading && !ui.loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (googleLoading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Conectando...")
                        } else {
                            Text("Entrar con Google")
                        }
                    }

                    // Divider "o"
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Divider(modifier = Modifier.weight(1f))
                        Text("  o  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Divider(modifier = Modifier.weight(1f))
                    }

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            clearLocalMessages()
                        },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password
                    OutlinedTextField(
                        value = pass,
                        onValueChange = {
                            pass = it
                            clearLocalMessages()
                        },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passVisible = !passVisible }) {
                                Text(if (passVisible) "Ocultar" else "Ver")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ✅ Recuperar contraseña
                    TextButton(
                        onClick = { sendResetEmail() },
                        enabled = !resetLoading && !ui.loading && !googleLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (resetLoading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enviando enlace...")
                        } else {
                            Icon(Icons.Filled.Password, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("¿Olvidaste tu contraseña?")
                        }
                    }

                    // Mensajes locales
                    localErr?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    localMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }

                    // Error de VM (login correo)
                    ui.error?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Botón Login con correo
                    Button(
                        onClick = {
                            clearLocalMessages()
                            val e = email.trim()
                            val p = pass

                            if (e.isBlank()) { localErr = "Ingresa tu correo"; return@Button }
                            if (p.isBlank()) { localErr = "Ingresa tu contraseña"; return@Button }

                            vm.login(e, p) { onLoggedIn() }
                        },
                        enabled = !ui.loading && !googleLoading && !resetLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (ui.loading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Ingresando...")
                        } else {
                            Text("Entrar con correo")
                        }
                    }

                    // Link a registro
                    TextButton(
                        onClick = onGoRegister,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No tengo cuenta → Registrarme")
                    }
                }
            }
        }
    }
}

private fun getDefaultWebClientId(context: Context): String {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resId == 0) "" else context.getString(resId)
}
