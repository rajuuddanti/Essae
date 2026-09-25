package com.mahamart.essae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mahamart.essae.cloud.SupabaseAuth
import kotlinx.coroutines.launch

class AdminActivity : ComponentActivity() {
    private lateinit var auth: SupabaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuth(applicationContext)

        setContent {
            MahaMartTheme {
                AdminScreen(
                    auth = auth,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AdminScreen(
    auth: SupabaseAuth,
    onClose: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var profile by remember { mutableStateOf<SupabaseAuth.AdminProfile?>(null) }

    LaunchedEffect(Unit) {
        if (auth.isSignedIn) {
            auth.restoreSession().onSuccess {
                profile = it
            }
        }
    }

    if (profile != null) {
        AdminDashboard(
            profile = profile!!,
            onLogout = {
                auth.signOut()
                profile = null
                password = ""
            },
            onClose = onClose
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("MahaMart Admin", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Administrator sign in", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = "" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            enabled = !loading
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = "" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !loading
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Enter email and password."
                    return@Button
                }
                loading = true
                error = ""
                // The composable cannot own the Activity lifecycle, so the login
                // is performed from a remembered coroutine scope below.
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("SIGN IN")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose, enabled = !loading) {
            Text("CANCEL")
        }
    }

    // Login side effect is separated so the UI remains simple and testable.
    if (loading) {
        LaunchedEffect(email, password, loading) {
            val result = auth.signIn(email, password)
            result.onSuccess {
                profile = it
                loading = false
            }.onFailure {
                error = it.message ?: "Login failed."
                loading = false
            }
        }
    }
}

@Composable
private fun AdminDashboard(
    profile: SupabaseAuth.AdminProfile,
    onLogout: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(profile.fullName.ifBlank { "MahaMart Admin" })
        Text(profile.role, style = MaterialTheme.typography.labelLarge)

        Spacer(Modifier.height(8.dp))
        Text("Admin authentication is connected successfully.")
        Text("Next modules: Admin Push, Reports, and Store/IP Mapping.")

        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("CLOSE")
        }
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("LOG OUT")
        }
    }
}
