package com.messagingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagingapp.ui.HomeScreen
import com.messagingapp.ui.auth.AuthScreen
import com.messagingapp.ui.auth.AuthViewModel
import com.messagingapp.ui.setup.SetupScreen
import com.messagingapp.ui.setup.SetupViewModel
import com.messagingapp.ui.theme.AppTheme
import io.github.jan.supabase.auth.auth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    // Determine initial screen based on session
    val isLoggedIn = remember {
        SupabaseClient.client.auth.currentUserOrNull() != null
    }

    var screen by remember {
        mutableStateOf(if (isLoggedIn) "checking" else "auth")
    }

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            val authRepo = com.messagingapp.data.repository.AuthRepository()
            val hasProfile = authRepo.hasProfile()
            screen = if (hasProfile) "home" else "setup"
        }
    }

    when (screen) {
        "auth" -> {
            val vm: AuthViewModel = viewModel()
            AuthScreen(viewModel = vm, onAuthenticated = { needsProfile ->
                screen = if (needsProfile) "setup" else "home"
            })
        }
        "setup" -> {
            val vm: SetupViewModel = viewModel()
            SetupScreen(viewModel = vm, onComplete = { screen = "home" })
        }
        "home" -> {
            HomeScreen(onLogout = { screen = "auth" })
        }
        else -> {
            // checking — show nothing or splash
        }
    }
}
