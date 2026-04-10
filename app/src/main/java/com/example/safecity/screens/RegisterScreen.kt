package com.example.safecity.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
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
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

@Composable
fun RegisterScreen(
    onGoLogin: () -> Unit,
    onRegistered: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ---------- Google Sign-In Launcher ----------
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        loading = false
        error = null

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)

            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                error = "No se obtuvo idToken. Revisa default_web_client_id y SHA-1 en Firebase."
                return@rememberLauncherForActivityResult
            }

            loading = true
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { t ->
                    loading = false
                    if (t.isSuccessful) {
                        // ✅ Entra directo
                        onRegistered()
                    } else {
                        error = t.exception?.message ?: "Error autenticando con Google"
                    }
                }
        } catch (e: ApiException) {
            // Muy típico: 10 = DEVELOPER_ERROR (SHA1 / json)
            error = "Google Sign-In error: ${e.statusCode}"
        } catch (e: Exception) {
            error = e.message ?: "Error con Google Sign-In"
        }
    }

    fun startGoogleSignIn() {
        error = null
        loading = true

        val clientId = getDefaultWebClientId(context)
        if (clientId.isBlank()) {
            loading = false
            error = "No se encontró default_web_client_id. Revisa google-services.json."
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(context, gso)
        client.signOut() // fuerza elegir cuenta siempre (opcional)
        googleLauncher.launch(client.signInIntent)
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
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
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
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text("Crear cuenta", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Regístrate para usar SafeCity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider()

                    // Google primero (entra directo)
                    OutlinedButton(
                        onClick = { startGoogleSignIn() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Conectando...")
                        } else {
                            Text("Continuar con Google")
                        }
                    }

                    // Divider "o"
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Divider(modifier = Modifier.weight(1f))
                        Text("  o  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Divider(modifier = Modifier.weight(1f))
                    }

                    // Nombre
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it; error = null },
                        label = { Text("Nombre completo") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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

                    if (!error.isNullOrBlank()) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }

                    // Registrar manual
                    Button(
                        onClick = {
                            error = null
                            val n = fullName.trim()
                            val e = email.trim()
                            val p = pass

                            if (n.length < 3) { error = "Ingresa tu nombre completo"; return@Button }
                            if (e.isBlank()) { error = "Ingresa tu correo"; return@Button }
                            if (p.length < 6) { error = "La contraseña debe tener mínimo 6 caracteres"; return@Button }

                            loading = true
                            auth.createUserWithEmailAndPassword(e, p)
                                .addOnCompleteListener { task ->
                                    if (!task.isSuccessful) {
                                        loading = false
                                        error = task.exception?.message ?: "Error registrando"
                                        return@addOnCompleteListener
                                    }

                                    val user = auth.currentUser
                                    if (user == null) {
                                        loading = false
                                        error = "No se encontró usuario"
                                        return@addOnCompleteListener
                                    }

                                    // ✅ Guardar nombre en el perfil (displayName)
                                    user.updateProfile(
                                        UserProfileChangeRequest.Builder()
                                            .setDisplayName(n)
                                            .build()
                                    ).addOnCompleteListener {
                                        loading = false
                                        onRegistered()
                                    }
                                }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Creando cuenta...")
                        } else {
                            Text("Registrar con correo")
                        }
                    }

                    TextButton(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("Ya tengo cuenta → Volver al login")
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
