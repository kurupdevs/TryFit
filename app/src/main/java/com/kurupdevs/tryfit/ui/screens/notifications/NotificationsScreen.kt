package com.kurupdevs.tryfit.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurupdevs.tryfit.data.db.NotificationEntity
import com.kurupdevs.tryfit.di.AppContainer
import com.kurupdevs.tryfit.ui.components.containerViewModel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsViewModel(private val container: AppContainer) : ViewModel() {

    val notifications = container.notificationsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            container.notificationsRepository.observe().collect {
                loading = false
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.notificationsRepository.markRead(id)
        }
    }

    fun markAllRead() {
        viewModelScope.launch(Dispatchers.IO) {
            container.notificationsRepository.markAllRead()
        }
    }
}

/**
 * Notifications — SPEC §3.7: list with unread dots, deep-link on
 * "try-on ready". Reads the local Room store (SPEC §6: last 50 offline).
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenResult: (sessionId: String) -> Unit,
) {
    val vm: NotificationsViewModel = containerViewModel { NotificationsViewModel(it) }
    val notifications by vm.notifications.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = TryFitColors.BgScreen,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = TryFitSpacing.ScreenPadding,
                        top = 12.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TryFitColors.TextPrimary,
                    )
                }
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    color = TryFitColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (notifications.any { !it.read }) {
                    TextButton(onClick = { vm.markAllRead() }) {
                        Text(
                            text = "Mark all read",
                            style = MaterialTheme.typography.labelLarge,
                            color = TryFitColors.TextSecondary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            vm.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = TryFitColors.TextPrimary)
            }
            notifications.isEmpty() -> NotificationsEmptyState(
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = TryFitSpacing.ScreenPadding,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = {
                            vm.markRead(notification.id)
                            // Deep-link: "tryonResult/<sessionId>" (SPEC §3.7).
                            notification.deepLink
                                ?.takeIf { it.startsWith("tryonResult/") }
                                ?.removePrefix("tryonResult/")
                                ?.takeIf { it.isNotBlank() }
                                ?.let(onOpenResult)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationEntity,
    onClick: () -> Unit,
) {
    val dateFmt = rememberDateFormat()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(TryFitRadii.GridCard),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.read) TryFitColors.SurfaceCard else TryFitColors.BgCanvas
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TryFitColors.SurfaceThumb),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notificationIcon(notification.type),
                    contentDescription = null,
                    tint = TryFitColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = TryFitColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notification.read) {
                        // Unread dot (SPEC §3.7).
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TryFitColors.AccentBadge),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateFmt.format(Date(notification.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TryFitColors.TextSecondary,
                )
            }
        }
    }
}

private fun notificationIcon(type: String): ImageVector = when (type) {
    "tryon_done" -> Icons.Filled.AutoAwesome
    "promo" -> Icons.Filled.LocalOffer
    else -> Icons.Filled.Settings
}

@Composable
private fun rememberDateFormat(): SimpleDateFormat {
    return androidx.compose.runtime.remember {
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    }
}

@Composable
private fun NotificationsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(TryFitColors.BgCanvas),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = TryFitColors.TextSecondary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "All caught up",
            style = MaterialTheme.typography.titleMedium,
            color = TryFitColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try-on results, offers and updates will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
        )
    }
}

/**
 * Notification deep-links ("tryonResult/<sessionId>") are handled in the
 * NavHost (`TRY_ON_RESULT` destination) so taps open the result directly.
 */
