package se.oscarb.bdai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bdai.shared.generated.resources.Res
import bdai.shared.generated.resources.model_setup_download
import bdai.shared.generated.resources.model_setup_retry
import bdai.shared.generated.resources.settings_close
import bdai.shared.generated.resources.settings_model_downloading
import bdai.shared.generated.resources.settings_model_failed
import bdai.shared.generated.resources.settings_model_loading
import bdai.shared.generated.resources.settings_model_not_downloaded
import bdai.shared.generated.resources.settings_model_ready
import bdai.shared.generated.resources.settings_model_section
import bdai.shared.generated.resources.settings_nas_section
import bdai.shared.generated.resources.settings_nas_url_label
import bdai.shared.generated.resources.settings_nas_url_placeholder
import bdai.shared.generated.resources.settings_notifications_7days
import bdai.shared.generated.resources.settings_notifications_enable
import bdai.shared.generated.resources.settings_notifications_section
import bdai.shared.generated.resources.settings_notifications_test
import bdai.shared.generated.resources.settings_pull_from_nas
import bdai.shared.generated.resources.settings_sync_error
import bdai.shared.generated.resources.settings_sync_success
import bdai.shared.generated.resources.settings_sync_to_nas
import bdai.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import se.oscarb.bdai.agent.domain.ModelState
import se.oscarb.bdai.core.designsystem.AppIcons
import se.oscarb.bdai.notifications.rememberNotificationPermissionRequester

@Composable
internal fun SettingsDialog(
    state: BdaiChatState,
    onAction: (BdaiAction) -> Unit
) {
    var nasUrlText by remember { mutableStateOf(state.settings.nasUrl) }

    val requestNotificationPermission = rememberNotificationPermissionRequester { granted ->
        onAction(BdaiAction.OnToggleNotifications(granted))
    }

    AlertDialog(
        onDismissRequest = {
            if (nasUrlText != state.settings.nasUrl) {
                onAction(BdaiAction.OnUpdateNasUrl(nasUrlText))
            }
            onAction(BdaiAction.OnDismissSettings)
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = AppIcons.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // NAS Sync Section
                Text(
                    text = stringResource(Res.string.settings_nas_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = nasUrlText,
                    onValueChange = {
                        nasUrlText = it
                        onAction(BdaiAction.OnUpdateNasUrl(it))
                    },
                    label = { Text(stringResource(Res.string.settings_nas_url_label)) },
                    placeholder = { Text(stringResource(Res.string.settings_nas_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAction(BdaiAction.OnSyncWithNasClick) },
                        enabled = !state.isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(Res.string.settings_sync_to_nas))
                        }
                    }
                    OutlinedButton(
                        onClick = { onAction(BdaiAction.OnPullFromNasClick) },
                        enabled = !state.isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.settings_pull_from_nas))
                    }
                }
                state.syncFeedbackCount?.let { count ->
                    Text(
                        text = stringResource(Res.string.settings_sync_success, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                state.syncErrorMessage?.let { errorMsg ->
                    Text(
                        text = stringResource(Res.string.settings_sync_error, errorMsg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider()

                // Notifications Section
                Text(
                    text = stringResource(Res.string.settings_notifications_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.settings_notifications_enable),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                requestNotificationPermission()
                            } else {
                                onAction(BdaiAction.OnToggleNotifications(false))
                            }
                        }
                    )
                }
                if (state.settings.notificationsEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_notifications_7days),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.settings.notifyDaysBefore,
                            onCheckedChange = { enabled ->
                                onAction(BdaiAction.OnToggleNotify7Days(enabled))
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { onAction(BdaiAction.OnTestNotificationsClick) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = AppIcons.Notification,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp)
                        )
                        Text(stringResource(Res.string.settings_notifications_test))
                    }
                }

                HorizontalDivider()

                // AI Model Section
                Text(
                    text = stringResource(Res.string.settings_model_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val statusText = when (val ms = state.modelState) {
                            ModelState.Ready -> stringResource(Res.string.settings_model_ready)
                            ModelState.NotDownloaded -> stringResource(Res.string.settings_model_not_downloaded)
                            is ModelState.Downloading -> {
                                val pct = if (ms.totalBytes > 0L) {
                                    ((ms.downloadedBytes * 100L) / ms.totalBytes).toInt()
                                } else 0
                                stringResource(Res.string.settings_model_downloading, pct)
                            }
                            ModelState.Loading -> stringResource(Res.string.settings_model_loading)
                            is ModelState.DownloadFailed -> stringResource(
                                Res.string.settings_model_failed,
                                ms.message ?: "Download error"
                            )
                            is ModelState.LoadFailed -> stringResource(
                                Res.string.settings_model_failed,
                                ms.message ?: "Load error"
                            )
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        when (state.modelState) {
                            ModelState.NotDownloaded -> {
                                Button(onClick = { onAction(BdaiAction.OnDownloadModelClick) }) {
                                    Text(stringResource(Res.string.model_setup_download, "2.6 GB"))
                                }
                            }
                            is ModelState.DownloadFailed -> {
                                OutlinedButton(onClick = { onAction(BdaiAction.OnDownloadModelClick) }) {
                                    Text(stringResource(Res.string.model_setup_retry))
                                }
                            }
                            is ModelState.LoadFailed -> {
                                OutlinedButton(onClick = { onAction(BdaiAction.OnRetryLoadModelClick) }) {
                                    Text(stringResource(Res.string.model_setup_retry))
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(BdaiAction.OnDismissSettings) }) {
                Text(stringResource(Res.string.settings_close))
            }
        }
    )
}
