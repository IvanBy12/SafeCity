package com.example.safecity.screens


import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

@Composable
fun PhoneAuthScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = remember { FirebaseAuth.getInstance() }

    var phone by remember { mutableStateOf("") } // Ej: +573001234567
    var code by remember { mutableStateOf("") }

    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendingToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-detección del SMS (a veces)
                loading = true
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    loading = false
                    if (task.isSuccessful) onSuccess()
                    else error = task.exception?.message ?: "Error autenticando"
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                loading = false
                error = e.message ?: "Falló la verificación"
            }

            override fun onCodeSent(
                vId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                loading = false
                verificationId = vId
                resendingToken = token
            }
        }
    }

    fun sendCode(resend: Boolean) {
        error = null

        if (activity == null) {
            error = "No se encontró Activity para Phone Auth"
            return
        }
        if (!phone.trim().startsWith("+")) {
            error = "Escribe el número con prefijo. Ej: +57..."
            return
        }

        loading = true

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (resend && resendingToken != null) {
            builder.setForceResendingToken(resendingToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyCode() {
        error = null
        val vId = verificationId ?: run {
            error = "Primero solicita el código"
            return
        }
        if (code.length < 4) {
            error = "Ingresa el código recibido"
            return
        }

        loading = true
        val credential = PhoneAuthProvider.getCredential(vId, code.trim())
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            loading = false
            if (task.isSuccessful) onSuccess()
            else error = task.exception?.message ?: "Código inválido"
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Registro por teléfono") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Atrás") }
                }
            )
        }
    ) { padding ->
        Card(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; error = null },
                    label = { Text("Teléfono (con prefijo)") },
                    placeholder = { Text("+573001234567") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { sendCode(resend = false) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Text("Enviar código (SMS)")
                }

                if (verificationId != null) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        label = { Text("Código (OTP)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { verifyCode() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verificar y entrar")
                    }

                    TextButton(
                        onClick = { sendCode(resend = true) },
                        enabled = !loading
                    ) {
                        Text("Reenviar código")
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun SmallTopAppBar(title: @Composable () -> Unit, navigationIcon: @Composable () -> Unit) {
    TODO("Not yet implemented")
}