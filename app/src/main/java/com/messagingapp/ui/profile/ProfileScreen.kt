package com.messagingapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagingapp.data.models.UserProfile
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.ui.messages.AvatarCircle
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val repo = AuthRepository()
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile = _profile.asStateFlow()
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut = _loggedOut.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            repo.getProfile(uid).onSuccess { _profile.value = it }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.signOut()
            _loggedOut.value = true
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(32.dp))

        // Avatar + info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(24.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarCircle(name = profile?.nickname ?: "?", size = 80)

            profile?.let { p ->
                Text(p.nickname, style = MaterialTheme.typography.headlineMedium)
                Text("@${p.username}", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textSecondary)

                Row(
                    modifier = Modifier
                        .glassCard(10.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val (icon, color, label) = if (p.visibility == "public")
                        Triple(Icons.Default.Public, GlassColors.success, "Public")
                    else
                        Triple(Icons.Default.Lock, GlassColors.warning, "Private")

                    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                }
            } ?: CircularProgressIndicator(color = GlassColors.primary, modifier = Modifier.size(24.dp))
        }

        Spacer(Modifier.height(20.dp))

        // Settings section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsRow(icon = Icons.Default.Notifications, label = "Notifications", onClick = {})
            SettingsRow(icon = Icons.Default.Info, label = "About Glimpse", onClick = {})
        }

        Spacer(Modifier.weight(1f))

        // Logout button
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassColors.error),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassColors.error.copy(0.5f))
        ) {
            Icon(Icons.Default.Logout, null, tint = GlassColors.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", color = GlassColors.error)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color(0xFF0D1525),
            shape = RoundedCornerShape(20.dp),
            title = { Text("Sign Out", color = GlassColors.textPrimary) },
            text = { Text("Are you sure you want to sign out?", color = GlassColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout() }) {
                    Text("Sign Out", color = GlassColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = GlassColors.textSecondary)
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(GlassColors.glassWhite, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = GlassColors.primary, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = GlassColors.textTertiary, modifier = Modifier.size(18.dp))
    }
}
