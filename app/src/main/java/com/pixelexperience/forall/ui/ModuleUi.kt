package com.pixelexperience.forall.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixelexperience.forall.core.modules.PixelModuleId

fun PixelModuleId.icon(): ImageVector = when (this) {
    PixelModuleId.LAUNCHER -> Icons.Rounded.Home
    PixelModuleId.THEMES -> Icons.Rounded.Create
    PixelModuleId.WIDGETS -> Icons.Rounded.DateRange
    PixelModuleId.CONTROL_CENTER -> Icons.Rounded.Settings
    PixelModuleId.NOTIFICATIONS -> Icons.Rounded.Notifications
    PixelModuleId.LOCKSCREEN -> Icons.Rounded.Lock
    PixelModuleId.SOUNDS -> Icons.Rounded.PlayArrow
    PixelModuleId.EXTRAS -> Icons.Rounded.Star
}
