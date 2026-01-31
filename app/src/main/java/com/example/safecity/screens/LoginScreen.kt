package com.example.safecity.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.safecity.auth.AuthViewModel

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onGoRegister: () -> Unit,
    onLoggedIn: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    // Limpia error cuando el usuario edita
    fun clearError() {
        // si tu vm no tiene función para limpiar, igual se “oculta” visualmente al modificar
        // porque aquí controlamos un mensaje local también (si quieres)
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

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; clearError() },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; clearError() },
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

                    // Error de VM
                    ui.error?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Botón Login
                    Button(
                        onClick = {
                            val e = email.trim()
                            val p = pass

                            // Validaciones rápidas antes de llamar VM
                            if (e.isBlank()) return@Button
                            if (p.isBlank()) return@Button

                            vm.login(e, p) { onLoggedIn() }
                        },
                        enabled = !ui.loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (ui.loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Ingresando...")
                        } else {
                            Text("Entrar")
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
