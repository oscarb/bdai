package se.oscarb.bdai.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import bdai.shared.generated.resources.Res
import bdai.shared.generated.resources.ic_build
import bdai.shared.generated.resources.ic_cake
import bdai.shared.generated.resources.ic_close
import bdai.shared.generated.resources.ic_download
import bdai.shared.generated.resources.ic_mic
import bdai.shared.generated.resources.ic_notification
import bdai.shared.generated.resources.ic_people
import bdai.shared.generated.resources.ic_plus
import bdai.shared.generated.resources.ic_refresh
import bdai.shared.generated.resources.ic_send
import bdai.shared.generated.resources.ic_settings
import bdai.shared.generated.resources.ic_sparkle
import bdai.shared.generated.resources.ic_sync
import bdai.shared.generated.resources.ic_trash
import org.jetbrains.compose.resources.painterResource

object AppIcons {
    val Sparkle: Painter @Composable get() = painterResource(Res.drawable.ic_sparkle)
    val Mic: Painter @Composable get() = painterResource(Res.drawable.ic_mic)
    val Send: Painter @Composable get() = painterResource(Res.drawable.ic_send)
    val People: Painter @Composable get() = painterResource(Res.drawable.ic_people)
    val Cake: Painter @Composable get() = painterResource(Res.drawable.ic_cake)
    val Settings: Painter @Composable get() = painterResource(Res.drawable.ic_settings)
    val Sync: Painter @Composable get() = painterResource(Res.drawable.ic_sync)
    val Refresh: Painter @Composable get() = painterResource(Res.drawable.ic_refresh)
    val Close: Painter @Composable get() = painterResource(Res.drawable.ic_close)
    val Plus: Painter @Composable get() = painterResource(Res.drawable.ic_plus)
    val Trash: Painter @Composable get() = painterResource(Res.drawable.ic_trash)
    val Notification: Painter @Composable get() = painterResource(Res.drawable.ic_notification)
    val Build: Painter @Composable get() = painterResource(Res.drawable.ic_build)
    val Download: Painter @Composable get() = painterResource(Res.drawable.ic_download)
}
