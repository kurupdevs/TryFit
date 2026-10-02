package com.kurupdevs.tryfit.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kurupdevs.tryfit.BuildConfig
import com.kurupdevs.tryfit.data.PhotoJanitor
import com.kurupdevs.tryfit.data.TryFitSettings
import com.kurupdevs.tryfit.di.AppContainer
import com.kurupdevs.tryfit.tryon.CameraCaptureScreen
import com.kurupdevs.tryfit.ui.components.PrivacyConsentSheet
import com.kurupdevs.tryfit.ui.components.containerViewModel
import com.kurupdevs.tryfit.ui.components.copyToSharedCache
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    val settings = container.settingsStore.settings
    val avatar = container.avatarStore.avatar

    var quotaText by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            quotaText = runCatching {
                val q = container.tryOnEngine.getQuota()
                "${q.remaining}/${q.limit} free left today · no card required"
            }.getOrNull()
        }
    }

    fun setName(name: String) {
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.setUserName(name) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.setLanguage(lang) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.setReducedMotion(enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.setHapticsEnabled(enabled) }
    }

    fun setAutoDeleteDays(days: Int) {
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.setAutoDeleteDays(days) }
    }

    fun saveAvatar(bytes: ByteArray, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            container.avatarStore.saveAvatar(bytes)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun removeAvatar(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            container.avatarStore.clearAvatar()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** One-tap delete: avatar + every uploaded/processed photo on the device. */
    fun deleteAllPhotos(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PhotoJanitor.deleteAllUserPhotos(context, container.avatarStore) }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.supabaseOrNull?.auth?.signOut() }
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}

/**
 * Settings — SPEC §3.7: avatar, name, body profile entry, language,
 * reduced motion, haptics, privacy (consent status, delete my photos,
 * export data, auto-delete timer), quota display, logout, version.
 *
 * Copy rules: 5/day free, "no card required" — NO paywall/subscription UI
 * anywhere (bloat trap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBodyProfileClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val vm: SettingsViewModel = containerViewModel { c -> SettingsViewModel(c, context) }

    val settings by vm.settings.collectAsState(initial = TryFitSettings())
    val avatarFile by vm.avatar.collectAsState(initial = null)

    var showAvatarSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var nameDraft by remember(settings.userName) { mutableStateOf(settings.userName) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
                    }.getOrNull()
                    if (bytes != null) {
                        vm.saveAvatar(bytes) {
                            scope.launch { snackbar.showSnackbar("Avatar updated") }
                        }
                    } else {
                        scope.launch { snackbar.showSnackbar("Couldn't read that photo.") }
                    }
                }
            }
        },
    )

    Scaffold(
        containerColor = TryFitColors.BgScreen,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = TryFitSpacing.ScreenPadding, top = 12.dp),
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
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = TryFitColors.TextPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TryFitSpacing.ScreenPadding),
        ) {
            // ---- Avatar row ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAvatarSheet = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarThumb(file = avatarFile)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.userName.ifBlank { "Your avatar" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TryFitColors.TextPrimary,
                    )
                    Text(
                        text = if (avatarFile != null) "Change or remove" else "Set your full-body photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TryFitColors.TextSecondary,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TryFitColors.TextSecondary,
                )
            }
            HorizontalDivider(color = TryFitColors.BgCanvas)
            Spacer(Modifier.height(8.dp))

            // ---- Name ----
            Text(
                text = "Name",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    placeholder = { Text("Your name") },
                    singleLine = true,
                    shape = TryFitRadii.Pill,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TryFitColors.TextPrimary,
                        unfocusedBorderColor = TryFitColors.BgCanvas,
                        cursorColor = TryFitColors.TextPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        vm.setName(nameDraft.trim())
                        scope.launch { snackbar.showSnackbar("Name saved") }
                    },
                ) {
                    Text(text = "Save", color = TryFitColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))

            SettingsEntryRow(
                icon = Icons.Filled.Person,
                title = "Body profile",
                subtitle = "Helps size suggestions — never a fit guarantee",
                onClick = onBodyProfileClick,
            )

            SectionHeader("Preferences")

            SettingsSwitchRow(
                title = "Reduced motion",
                subtitle = "Disables shimmer, confetti and reveal animations",
                checked = settings.reducedMotion,
                onChecked = { vm.setReducedMotion(it) },
            )
            SettingsSwitchRow(
                title = "Haptics",
                subtitle = "Subtle vibration on taps and result reveal",
                checked = settings.hapticsEnabled,
                onChecked = { vm.setHaptics(it) },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Language",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                LanguageChip(label = "English", selected = settings.language == "en") {
                    vm.setLanguage("en")
                }
                Spacer(Modifier.width(8.dp))
                LanguageChip(label = "हिंदी", selected = settings.language == "hi") {
                    vm.setLanguage("hi")
                }
            }

            SectionHeader("Try-on quota")
            Text(
                text = vm.quotaText ?: "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = "Free forever, no card required. Quota resets at midnight.",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
            )

            SectionHeader("Privacy")

            SettingsEntryRow(
                icon = Icons.Filled.Shield,
                title = "Photo consent",
                subtitle = if (settings.cameraConsentGiven)
                    "Given — on-device first, auto-delete, never used to train models"
                else
                    "Not given yet — you'll be asked before the camera is used",
                onClick = { /* informational; consent is captured in the sheet */ },
                showChevron = false,
            )
            SettingsEntryRow(
                icon = Icons.Filled.FileDownload,
                title = "Export my data",
                subtitle = "Downloads your profile, wardrobe and settings as JSON",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val file = runCatching { exportDataJson(context, container) }.getOrNull()
                        withContext(Dispatchers.Main) {
                            if (file != null) {
                                val shared = runCatching {
                                    copyToSharedCache(context, file, "tryfit-data-export.json")
                                }.getOrNull() ?: file
                                shareJsonFile(context, shared)
                                scope.launch { snackbar.showSnackbar("Data exported") }
                            } else {
                                scope.launch { snackbar.showSnackbar("Export failed — please try again.") }
                            }
                        }
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-delete uploads",
                        style = MaterialTheme.typography.labelLarge,
                        color = TryFitColors.TextPrimary,
                    )
                    Text(
                        text = "Raw photos are deleted from this device automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TryFitColors.TextSecondary,
                    )
                }
                listOf(7, 30).forEach { days ->
                    FilterChip(
                        selected = settings.autoDeleteDays == days,
                        onClick = { vm.setAutoDeleteDays(days) },
                        label = {
                            Text(
                                text = "${days}d",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (settings.autoDeleteDays == days)
                                    TryFitColors.TextPrimary else TryFitColors.TextSecondary,
                            )
                        },
                        shape = TryFitRadii.Pill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TryFitColors.SurfaceChipSelected,
                            containerColor = Color.Transparent,
                        ),
                        border = null,
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            SettingsEntryRow(
                icon = Icons.Filled.Delete,
                title = "Delete my photos",
                subtitle = "Removes your avatar and every uploaded photo from this device",
                onClick = { showDeleteConfirm = true },
                showChevron = false,
                destructive = true,
            )

            SectionHeader("Account")
            SettingsEntryRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Sign out",
                subtitle = "You'll stay signed out on this device",
                onClick = { showLogoutConfirm = true },
                showChevron = false,
                destructive = true,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "TryFit ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
            )
            Text(
                text = "Made with care · AI previews, honest labels",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
            )
        }
    }

    // ---- Avatar source sheet ----
    if (showAvatarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TryFitColors.BgScreen,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Set your avatar",
                    style = MaterialTheme.typography.titleMedium,
                    color = TryFitColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "One full-body photo, reused as the default input for every try-on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                AvatarSourceRow(
                    icon = Icons.Filled.CameraAlt,
                    label = "Take a photo",
                    onClick = {
                        showAvatarSheet = false
                        if (settings.cameraConsentGiven) showCamera = true
                        else showConsent = true
                    },
                )
                AvatarSourceRow(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Choose from gallery",
                    onClick = {
                        showAvatarSheet = false
                        galleryLauncher.launch("image/*")
                    },
                )
                if (avatarFile != null) {
                    AvatarSourceRow(
                        icon = Icons.Filled.Close,
                        label = "Remove avatar",
                        destructive = true,
                        onClick = {
                            showAvatarSheet = false
                            vm.removeAvatar {
                                scope.launch { snackbar.showSnackbar("Avatar removed") }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showConsent) {
        PrivacyConsentSheet(
            onAccept = {
                scope.launch(Dispatchers.IO) { container.settingsStore.setCameraConsent(true) }
                showConsent = false
                showCamera = true
            },
            onDismiss = { showConsent = false },
        )
    }

    if (showCamera) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CameraCaptureScreen(
                onPhotoCaptured = { file ->
                    showCamera = false
                    scope.launch(Dispatchers.IO) {
                        val bytes = runCatching { file.readBytes() }.getOrNull()
                        if (bytes != null) {
                            vm.saveAvatar(bytes) {
                                scope.launch { snackbar.showSnackbar("Avatar updated") }
                            }
                        }
                    }
                },
                onFallbackToGallery = {
                    showCamera = false
                    galleryLauncher.launch("image/*")
                },
                onClose = { showCamera = false },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all my photos?") },
            text = {
                Text("This removes your avatar and every uploaded or processed photo from this device. It can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteAllPhotos {
                        scope.launch { snackbar.showSnackbar("All photos deleted from this device") }
                    }
                }) {
                    Text(text = "Delete", color = Color(0xFFD64545))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    vm.logout(onLogout)
                }) {
                    Text(text = "Sign out", color = Color(0xFFD64545))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

/** Shares the exported JSON via the app FileProvider (read-only grant). */
private fun shareJsonFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export data"))
}

@Composable
private fun AvatarThumb(file: File?) {
    val context = LocalContext.current
    if (file != null && file.exists()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(file).size(256).build(),
            contentDescription = "Your avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(TryFitColors.BgCanvas),
        )
    } else {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(TryFitColors.BgCanvas),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = TryFitColors.TextSecondary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun AvatarSourceRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) Color(0xFFD64545) else TryFitColors.TextPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) Color(0xFFD64545) else TryFitColors.TextPrimary,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = TryFitColors.TextSecondary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    destructive: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) Color(0xFFD64545) else TryFitColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) Color(0xFFD64545) else TryFitColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary,
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TryFitColors.TextSecondary,
                )
            }
        }
        HorizontalDivider(color = TryFitColors.BgCanvas)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) TryFitColors.TextPrimary else TryFitColors.TextSecondary,
                )
            }
        },
        shape = TryFitRadii.Pill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = TryFitColors.SurfaceChipSelected,
            containerColor = Color.Transparent,
        ),
        border = null,
    )
}

/**
 * Builds the export payload (profile + wardrobe + settings + history counts).
 * Hand-rolled JSON — no extra serializer dependency.
 */
private suspend fun exportDataJson(context: Context, container: AppContainer): File {
    val settings = container.settingsStore.settings.first()
    val profile = container.bodyProfileStore.profile.first()
    val wardrobe = container.wardrobeRepository.observeItems().first()
    val historyCount = container.historyRepository.observeHistory().first().size
    val avatarPresent = container.avatarStore.hasAvatar()

    fun esc(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    val sb = StringBuilder()
    sb.appendLine("{")
    sb.appendLine("  \"exported_at\": ${System.currentTimeMillis()},")
    sb.appendLine("  \"app\": \"TryFit\",")
    sb.appendLine("  \"profile\": {")
    sb.appendLine("    \"user_name\": \"${esc(settings.userName)}\",")
    sb.appendLine("    \"language\": \"${settings.language}\",")
    sb.appendLine("    \"height_cm\": ${profile.heightCm},")
    sb.appendLine("    \"build\": \"${esc(profile.build.label)}\",")
    sb.appendLine("    \"chest_cm\": ${profile.chestCm},")
    sb.appendLine("    \"waist_cm\": ${profile.waistCm},")
    sb.appendLine("    \"fit_intent\": \"${esc(profile.fitIntent.label)}\"")
    sb.appendLine("  },")
    sb.appendLine("  \"avatar_present\": $avatarPresent,")
    sb.appendLine("  \"wardrobe\": [")
    wardrobe.forEachIndexed { i, item ->
        val comma = if (i < wardrobe.size - 1) "," else ""
        sb.appendLine("    {\"product_id\": \"${esc(item.productId)}\", \"added_at\": ${item.addedAt}}$comma")
    }
    sb.appendLine("  ],")
    sb.appendLine("  \"try_on_history_count\": $historyCount,")
    sb.appendLine("  \"settings\": {")
    sb.appendLine("    \"reduced_motion\": ${settings.reducedMotion},")
    sb.appendLine("    \"haptics\": ${settings.hapticsEnabled},")
    sb.appendLine("    \"auto_delete_days\": ${settings.autoDeleteDays},")
    sb.appendLine("    \"camera_consent_given\": ${settings.cameraConsentGiven}")
    sb.appendLine("  }")
    sb.appendLine("}")
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "tryfit-data-export.json")
    file.writeText(sb.toString())
    return file
}
