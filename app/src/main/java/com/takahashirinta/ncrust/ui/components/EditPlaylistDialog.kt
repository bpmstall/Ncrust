package com.takahashirinta.ncrust.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import io.github.takahashirinta.kanesumi.controls.MetroProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takahashirinta.ncrust.network.PlaylistApi
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import io.github.takahashirinta.kanesumi.controls.MetroDialog
import io.github.takahashirinta.kanesumi.controls.MetroDivider
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroTypography
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import kotlinx.coroutines.launch

@Composable
fun EditPlaylistDialog(
    playlistId: Long,
    initialName: String,
    initialDesc: String = "",
    onDismiss: () -> Unit,
    onSuccess: (newName: String, newDesc: String) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDesc) }
    var isSaving by remember { mutableStateOf(false) }

    fun handleSave() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || isSaving) return
        isSaving = true
        coroutineScope.launch {
            try {
                val ok = PlaylistApi.updatePlaylistInfo(playlistId, trimmedName, desc.trim())
                if (ok) {
                    Toast.makeText(context, strings.editPlaylistSuccess, Toast.LENGTH_SHORT).show()
                    onSuccess(trimmedName, desc.trim())
                    onDismiss()
                } else {
                    Toast.makeText(context, "更新失败，请重试", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "更新失败", Toast.LENGTH_SHORT).show()
            } finally {
                isSaving = false
            }
        }
    }

    MetroDialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 对话框标题
            MetroText(
                text = strings.editPlaylistTitle,
                color = LocalMetroColors.current.onBackground,
                style = LocalMetroTypography.current.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(16.dp))

            // 歌单名称输入
            MetroText(
                text = strings.editPlaylistNameLabel,
                color = LocalMetroColors.current.onSurfaceVariant,
                style = LocalMetroTypography.current.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalMetroColors.current.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        width = 0.75.dp,
                        color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = LocalMetroColors.current.onBackground,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(LocalMetroColors.current.primary),
                    decorationBox = { innerTextField ->
                        if (name.isEmpty()) {
                            MetroText(
                                text = strings.editPlaylistNameLabel,
                                color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.5f),
                                style = LocalMetroTypography.current.bodyMedium
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // 歌单描述输入
            MetroText(
                text = strings.editPlaylistDescLabel,
                color = LocalMetroColors.current.onSurfaceVariant,
                style = LocalMetroTypography.current.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalMetroColors.current.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        width = 0.75.dp,
                        color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 500) desc = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = LocalMetroColors.current.onBackground,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(LocalMetroColors.current.primary),
                    decorationBox = { innerTextField ->
                        if (desc.isEmpty()) {
                            MetroText(
                                text = "可选输入歌单描述与介绍",
                                color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.5f),
                                style = LocalMetroTypography.current.bodySmall
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(Modifier.height(20.dp))
            MetroDivider()
            Spacer(Modifier.height(12.dp))

            // 底部按钮栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 取消
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isSaving) { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    MetroText(
                        text = strings.cancelText,
                        color = LocalMetroColors.current.onSurfaceVariant,
                        style = LocalMetroTypography.current.bodyMedium
                    )
                }

                Spacer(Modifier.width(8.dp))

                // 保存
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LocalMetroColors.current.primary.copy(alpha = if (name.trim().isNotEmpty()) 0.15f else 0.05f))
                        .border(
                            width = 0.75.dp,
                            color = LocalMetroColors.current.primary.copy(alpha = if (name.trim().isNotEmpty()) 0.5f else 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(enabled = !isSaving && name.trim().isNotEmpty()) { handleSave() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isSaving) {
                        MetroProgressIndicator(
                            sizeDp = 16.dp,
                            color = LocalMetroColors.current.primary
                        )
                    } else {
                        MetroText(
                            text = strings.saveText,
                            color = LocalMetroColors.current.primary,
                            style = LocalMetroTypography.current.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
