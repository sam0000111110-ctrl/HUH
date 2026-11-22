package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PersonAddDisabled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary

/**
 * Data model for restricted keywords with category tags
 */
data class RestrictedKeyword(
    val word: String,
    val isCustom: Boolean = true
)

/**
 * Full Moderation Dashboard Bottom Sheet for live stream creators
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LiveStreamModerationSheet(
    blockedUsers: Set<String>,
    restrictedKeywords: Set<String>,
    activeViewers: List<String>,
    onBlockUser: (String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var newKeywordInput by remember { mutableStateOf("") }
    var newUserToBlockInput by remember { mutableStateOf("") }
    var showConfirmClearChatDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("moderation_dashboard_sheet")
        ) {
            // Header: Title + Security Shield + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Brush.linearGradient(listOf(AyerPrimary, AyerSecondary)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "درع الإشراف",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "لوحة إشراف البث 🛡️",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "حظر المستخدمين، الكلمات المحظورة، ومسح المحادثة فورياً",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .testTag("close_moderation_dashboard")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Tabs (0: Blocked Users, 1: Restricted Keywords, 2: Chat Moderation)
            val tabs = listOf(
                "المستخدمون المحظورون (${blockedUsers.size})",
                "الكلمات المقيدة (${restrictedKeywords.size})",
                "إدارة المحادثة 🧹"
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AyerCyan,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("moderation_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) AyerCyan else Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("moderation_tab_$index")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TAB 0: BLOCKED USERS
            when (selectedTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Quick Add User to Block
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newUserToBlockInput,
                                onValueChange = { newUserToBlockInput = it },
                                placeholder = {
                                    Text("اكتب اسم المستخدم لحظره...", fontSize = 12.sp, color = Color.Gray)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("block_user_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B),
                                    focusedBorderColor = Color(0xFFEF4444),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newUserToBlockInput.isNotBlank()) {
                                        onBlockUser(newUserToBlockInput.trim())
                                        newUserToBlockInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("confirm_block_user_button"),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حظر", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick action: Active Viewers List with 1-tap block
                        if (activeViewers.isNotEmpty()) {
                            Text(
                                text = "المشاهدون النشطون في البث (انقر للحظر السريع):",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeViewers.take(6).forEach { viewer ->
                                    val isBlocked = blockedUsers.contains(viewer)
                                    FilterChip(
                                        selected = isBlocked,
                                        onClick = {
                                            if (isBlocked) onUnblockUser(viewer) else onBlockUser(viewer)
                                        },
                                        label = {
                                            Text(
                                                text = if (isBlocked) "$viewer (محظور)" else viewer,
                                                fontSize = 11.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.PersonAddDisabled,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFF1E293B),
                                            labelColor = Color.White,
                                            selectedContainerColor = Color(0xFF991B1B),
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Blocked Users List
                        Text(
                            text = "قائمة المحظورين حالياً:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AyerGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (blockedUsers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "لا يوجد أي مستخدم محظور في هذا البث",
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(blockedUsers.toList()) { user ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("blocked_user_row_$user")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = user,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { onUnblockUser(user) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                                                shape = RoundedCornerShape(16.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp).testTag("unblock_button_$user")
                                            ) {
                                                Text("إلغاء الحظر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: RESTRICTED KEYWORDS
                1 -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "أي رسالة تحتوي على هذه الكلمات سيتم فلترتها ومنع ظهورها تلقائياً للمشاهدين 🚫",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Add Keyword Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newKeywordInput,
                                onValueChange = { newKeywordInput = it },
                                placeholder = {
                                    Text("أضف كلمة أو عبارة مقيدة جديدة...", fontSize = 12.sp, color = Color.Gray)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_restricted_keyword_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B),
                                    focusedBorderColor = AyerCyan,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newKeywordInput.isNotBlank()) {
                                        onAddKeyword(newKeywordInput.trim())
                                        newKeywordInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AyerCyan),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("confirm_add_keyword_button"),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Keyword Chips Grid
                        Text(
                            text = "قائمة الكلمات والعبارات المحظورة (${restrictedKeywords.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AyerGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                                .padding(10.dp)
                        ) {
                            if (restrictedKeywords.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "لا توجد كلمات مقيدة حالياً. أضف كلمات لمنع الإساءة.",
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            restrictedKeywords.forEach { word ->
                                                Surface(
                                                    color = Color(0xFF334155),
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                                    modifier = Modifier.testTag("restricted_keyword_chip_$word")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.FilterAlt,
                                                            contentDescription = null,
                                                            tint = AyerGold,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = word,
                                                            fontSize = 12.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "حذف الكلمة",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clickable { onRemoveKeyword(word) }
                                                                .testTag("remove_keyword_$word")
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: CHAT CLEANUP & MODERATION
                2 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "مسح سجل محادثة البث فورياً 🧹",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "سيؤدي هذا الإجراء إلى حذف جميع الرسائل السابقة فورياً من شاشة البث لدى جميع المشاهدين في الوقت الفعلي مع إشعار نظام أمان.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { showConfirmClearChatDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("creator_clear_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "مسح المحادثة الآن (Clear Chat)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Safety Status
                        Surface(
                            color = Color(0xFF0F243A),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AyerCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = AyerCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "نظام الحماية الفوري نشط",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AyerCyan
                                    )
                                    Text(
                                        text = "يتم فحص كل تعليق فور إرساله ضد قائمة الكلمات المقيدة والمستخدمين المحظورين.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Confirmation Dialog for Clearing Chat
    if (showConfirmClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearChatDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تأكيد مسح محادثة البث", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "هل أنت متأكد من رغبتك في مسح جميع رسائل المحادثة الحالية؟ لا يمكن التراجع عن هذا الإجراء وسيتم تنظيف الشاشة لجميع المشاهدين فوراً.",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearChat()
                        showConfirmClearChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_clear_chat_dialog_button")
                ) {
                    Text("نعم، امسح المحادثة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearChatDialog = false }) {
                    Text("إلغاء", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}
