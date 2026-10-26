package se.oscarb.bdai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bdai.shared.generated.resources.Res
import bdai.shared.generated.resources.model_setup_description
import bdai.shared.generated.resources.model_setup_download
import bdai.shared.generated.resources.model_setup_download_failed
import bdai.shared.generated.resources.model_setup_downloading
import bdai.shared.generated.resources.model_setup_load_failed
import bdai.shared.generated.resources.model_setup_loading
import bdai.shared.generated.resources.model_setup_progress
import bdai.shared.generated.resources.model_setup_retry
import bdai.shared.generated.resources.model_setup_title
import org.jetbrains.compose.resources.stringResource
import se.oscarb.bdai.agent.domain.ModelState
import se.oscarb.bdai.core.designsystem.AppIcons
import se.oscarb.bdai.core.designsystem.AppTheme

private const val MODEL_DISPLAY_NAME = "Gemma 4 E2B"
private const val MODEL_DEFAULT_SIZE_BYTES = 2_588_147_712L

@Composable
internal fun ModelSetupCard(
    modelState: ModelState,
    onDownloadClick: () -> Unit,
    onRetryLoadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painter = AppIcons.Sparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            when (modelState) {
                ModelState.NotDownloaded -> {
                    Text(
                        text = stringResource(Res.string.model_setup_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    SetupText(
                        text = stringResource(
                            Res.string.model_setup_description,
                            MODEL_DISPLAY_NAME,
                            MODEL_DEFAULT_SIZE_BYTES.toGigabytesText()
                        )
                    )
                    Button(onClick = onDownloadClick) {
                        Icon(
                            painter = AppIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(
                                Res.string.model_setup_download,
                                MODEL_DEFAULT_SIZE_BYTES.toGigabytesText()
                            )
                        )
                    }
                }
                is ModelState.Downloading -> {
                    val progress = if (modelState.totalBytes > 0L) {
                        (modelState.downloadedBytes.toFloat() / modelState.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        text = stringResource(Res.string.model_setup_downloading, MODEL_DISPLAY_NAME),
                        style = MaterialTheme.typography.titleMedium
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SetupText(
                        text = stringResource(
                            Res.string.model_setup_progress,
                            modelState.downloadedBytes.toGigabytesText(),
                            modelState.totalBytes.takeIf { it > 0L }?.toGigabytesText()
                                ?: MODEL_DEFAULT_SIZE_BYTES.toGigabytesText()
                        )
                    )
                }
                ModelState.Loading -> {
                    CircularProgressIndicator()
                    SetupText(text = stringResource(Res.string.model_setup_loading))
                }
                is ModelState.DownloadFailed -> {
                    SetupText(
                        text = modelState.message?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.model_setup_download_failed),
                        isError = true
                    )
                    OutlinedButton(onClick = onDownloadClick) {
                        Text(text = stringResource(Res.string.model_setup_retry))
                    }
                }
                is ModelState.LoadFailed -> {
                    SetupText(
                        text = modelState.message?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.model_setup_load_failed),
                        isError = true
                    )
                    OutlinedButton(onClick = onRetryLoadClick) {
                        Text(text = stringResource(Res.string.model_setup_retry))
                    }
                }
                ModelState.Ready -> Unit
            }
        }
    }
}

@Composable
private fun SetupText(
    text: String,
    isError: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

internal fun Long.toGigabytesText(): String {
    val tenthsOfGigabyte = (this + 50_000_000) / 100_000_000
    return "${tenthsOfGigabyte / 10}.${tenthsOfGigabyte % 10} GB"
}

@Preview(name = "Model Not Downloaded")
@Composable
private fun ModelSetupCardPreview() {
    AppTheme {
        ModelSetupCard(
            modelState = ModelState.NotDownloaded,
            onDownloadClick = {},
            onRetryLoadClick = {}
        )
    }
}
