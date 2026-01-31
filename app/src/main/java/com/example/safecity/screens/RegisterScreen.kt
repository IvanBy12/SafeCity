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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun RegisterScreen(
    onGoLogin: () -> Unit,
    onRegistered: () -> Unit,
    onGoPhoneRegister: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ---------- Google Sign-In Launcher ----------
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // No pongas loading=false antes del try si quieres consistencia
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)

            // Aquí seguimos con Firebase
            signInWithGoogleToFirebase(
                account = account,
                auth = auth,
                onError = { msg ->
                    loading = false
                    error = msg
                },
                onSuccess = {
                    loading = false
                    onRegistered()
                }
            )
        } catch (e: Exception) {
            loading = false
            error = e.message ?: "Error con Google Sign-In"
        }
    }

    fun startGoogleSignIn() {
        error = null
        loading = true

        val clientId = getDefaultWebClientId(context)
        if (clientId.isBlank()) {
            loading = false
            error = "No se encontró default_web_client_id. Revisa google-services.json y SHA-1 en Firebase."
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(context, gso)
        client.signOut() // opcional: fuerza escoger cuenta
        googleLauncher.launch(client.signInIntent)
    }

    // ---------- UI ----------
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
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column {
                            Text(
                                text = "Crear cuenta",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Regístrate para reportar incidencias en SafeCity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider()

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; error = null },
                        label = { Text("Contraseña (mín. 6)") },
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

                    // Error
                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Email/Pass Register Button (FirebaseAuth directo)
                    Button(
                        onClick = {
                            error = null

                            val e = email.trim()
                            val p = pass

                            if (e.isBlank()) { error = "Ingresa tu correo"; return@Button }
                            if (p.length < 6) { error = "La contraseña debe tener mínimo 6 caracteres"; return@Button }

                            loading = true
                            auth.createUserWithEmailAndPassword(e, p)
                                .addOnCompleteListener { task ->
                                    loading = false
                                    if (task.isSuccessful) {
                                        onRegistered()
                                    } else {
                                        error = task.exception?.message ?: "Error registrando"
                                    }
                                }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Creando cuenta...")
                        } else {
                            Text("Registrar con correo")
                        }
                    }

                    // Divider "o"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Divider(modifier = Modifier.weight(1f))
                        Text(
                            "  o  ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Divider(modifier = Modifier.weight(1f))
                    }

                    // Google Button
                    OutlinedButton(
                        onClick = { startGoogleSignIn() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Continuar con Google")
                    }

                    // Phone Button
                    OutlinedButton(
                        onClick = onGoPhoneRegister,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Registrarme con teléfono")
                    }

                    // Go Login
                    TextButton(
                        onClick = onGoLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ya tengo cuenta → Volver al login")
                    }
                }
            }
        }
    }
}

private fun signInWithGoogleToFirebase(
    account: GoogleSignInAccount,
    auth: FirebaseAuth,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    val idToken = account.idToken
    if (idToken.isNullOrBlank()) {
        onError("No se obtuvo idToken. Revisa default_web_client_id y SHA-1 en Firebase.")
        return
    }

    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) onSuccess()
            else onError(task.exception?.message ?: "Error autenticando con Google")
        }
}

private fun getDefaultWebClientId(context: Context): String {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resId == 0) "" else context.getString(resId)
}
