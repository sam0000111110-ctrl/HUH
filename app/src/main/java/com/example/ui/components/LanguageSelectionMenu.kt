package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.HuhTheme

/**
 * Settings Row Component for Language Selection
 */
@Composable
fun LanguageSelectionSettingRow(
    currentLanguage: AppLanguage,
    onOpenLanguageDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLanguageDialog)
            .testTag("language_selection_setting_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(HuhTheme.colors.tertiary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = HuhTheme.colors.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = AppStrings.get("language_menu_title", currentLanguage),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HuhTheme.colors.textPrimary
                )
                Text(
                    text = AppStrings.get("language_menu_subtitle", currentLanguage),
                    fontSize = 11.sp,
                    color = HuhTheme.colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = HuhTheme.colors.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HuhTheme.colors.tertiary.copy(alpha = 0.4f)),
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentLanguage.flagEmoji,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = currentLanguage.nativeName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.tertiary
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = HuhTheme.colors.textTertiary,
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Modal Bottom Sheet for Language Selection Menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionBottomSheet(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tempSelectedLanguage by remember { mutableStateOf(currentLanguage) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HuhTheme.colors.surfaceElevated,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(HuhTheme.colors.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppStrings.get("select_language_dialog_title", tempSelectedLanguage),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "${AppLanguage.entries.size} لغات مدعومة حالياً",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = HuhTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Language Options List
            AppLanguage.entries.forEach { lang ->
                val isSelected = lang == tempSelectedLanguage
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            tempSelectedLanguage = lang
                            onLanguageSelected(lang)
                        }
                        .testTag("language_option_${lang.code}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            HuhTheme.colors.primary.copy(alpha = 0.15f)
                        else
                            HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.outline.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = lang.flagEmoji,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = lang.nativeName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(${lang.displayName})",
                                        fontSize = 12.sp,
                                        color = HuhTheme.colors.textSecondary
                                    )
                                }
                                Text(
                                    text = if (lang.isRtl) "اتجاه من اليمين لليسار (RTL)" else "اتجاه من اليسار لليمين (LTR)",
                                    fontSize = 10.sp,
                                    color = if (isSelected) HuhTheme.colors.tertiary else HuhTheme.colors.textTertiary
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(HuhTheme.colors.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "المحدد",
                                    tint = HuhTheme.colors.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Button
            Button(
                onClick = {
                    onLanguageSelected(tempSelectedLanguage)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_language_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = HuhTheme.colors.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${AppStrings.get("confirm", tempSelectedLanguage)} (${tempSelectedLanguage.nativeName})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
