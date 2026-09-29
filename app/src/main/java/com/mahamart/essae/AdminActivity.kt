package com.mahamart.essae

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mahamart.essae.cloud.SupabaseAuth

class AdminActivity : ComponentActivity() {
    private lateinit var auth: SupabaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuth(applicationContext)
        setContent { MahaMartTheme { AdminScreen(auth = auth, onClose = { finish() }) } }
    }
}

@Composable
private fun AdminScreen(auth: SupabaseAuth, onClose: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var profile by remember { mutableStateOf<SupabaseAuth.AdminProfile?>(null) }

    LaunchedEffect(Unit) {
        if (auth.isSignedIn) auth.restoreSession().onSuccess { profile = it }
    }
    if (profile != null) {
        AdminDashboard(profile!!, auth, { auth.signOut(); profile = null; password = "" }, onClose)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("MahaMart Admin", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp)); Text("Administrator sign in")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it; error = "" }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, enabled = !loading)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it; error = "" }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !loading)
        if (error.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (email.isBlank() || password.isBlank()) { error = "Enter email and password."; return@Button }
            loading = true; error = ""
        }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp) else Text("SIGN IN")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose, enabled = !loading) { Text("CANCEL") }
    }

    if (loading) {
        LaunchedEffect(email, password, loading) {
            auth.signIn(email, password)
                .onSuccess { profile = it; loading = false }
                .onFailure { error = it.message ?: "Login failed."; loading = false }
        }
    }
}

@Composable
private fun AdminDashboard(
    profile: SupabaseAuth.AdminProfile,
    auth: SupabaseAuth,
    onLogout: () -> Unit,
    onClose: () -> Unit
) {
    var showDeviceRegistration by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showDeviceRegistration) {
        AdminDeviceRegistration(auth, { showDeviceRegistration = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(profile.fullName.ifBlank { "MahaMart Admin" })
        Text(profile.role, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text("Admin authentication is connected successfully.")

        Button({ context.startActivity(Intent(context, AdminPushActivity::class.java)) }, Modifier.fillMaxWidth()) { Text("ADMIN PUSH") }
        Text("Publish a price to all stores or selected stores.", style = MaterialTheme.typography.bodySmall)

        Button({ context.startActivity(Intent(context, AdminCsvPushActivity::class.java)) }, Modifier.fillMaxWidth()) { Text("ADMIN CSV PUSH") }
        Text("Import a full price CSV and push it remotely to selected or all stores.", style = MaterialTheme.typography.bodySmall)

        Button({ showDeviceRegistration = true }, Modifier.fillMaxWidth()) { Text("STORE DEVICE REGISTRATION") }
        Text("Generate a one-time registration code for a physical store device.", style = MaterialTheme.typography.bodySmall)

        Button({ context.startActivity(Intent(context, AdminStoreDeviceMappingActivity::class.java)) }, Modifier.fillMaxWidth()) { Text("STORE / DEVICE MAPPING") }
        Text("View registered devices, pending prices and latest completed uploads.", style = MaterialTheme.typography.bodySmall)

        Button({ context.startActivity(Intent(context, AdminOperationsActivity::class.java)) }, Modifier.fillMaxWidth()) { Text("STORE OPERATIONS") }
        Text("View every registered Android device and its permanent store mapping.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("CLOSE") }
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("LOG OUT") }
    }
}
