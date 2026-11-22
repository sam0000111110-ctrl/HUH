package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PointTransaction
import com.example.ui.AyerViewModel
import com.example.ui.components.TransactionHistorySkeletonLoader
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurface
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TransactionFilterType {
    ALL,
    EARNINGS_ONLY,
    EXPENDITURES_ONLY
}

@Composable
fun TransactionHistoryScreen(
    viewModel: AyerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(TransactionFilterType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Fallback transactions if Room DB has not accumulated history yet
    val displayTransactions = remember(transactions, selectedFilter, searchQuery) {
        val baseList = if (transactions.isEmpty()) {
            listOf(
                PointTransaction(
                    id = 1,
                    type = "DAILY_FREE",
                    amount = 10,
                    description = "مكافأة الحضور اليومي المجانية",
                    timestamp = System.currentTimeMillis() - 3600000L * 2
                ),
                PointTransaction(
                    id = 2,
                    type = "GIFT_SENT",
                    amount = -50,
                    description = "إرسال هدية (برج إيفل الذهبي) في بث سارة القحطاني",
                    timestamp = System.currentTimeMillis() - 3600000L * 5
                ),
                PointTransaction(
                    id = 3,
                    type = "MISSION_REWARD",
                    amount = 35,
                    description = "مكافأة إتمام مهمة: مشاهدة البث المباشر (+35 نقطة)",
                    timestamp = System.currentTimeMillis() - 3600000L * 8
                ),
                PointTransaction(
                    id = 4,
                    type = "PACKAGE_PURCHASE",
                    amount = 500,
                    description = "شراء باقة الذهب (500 نقطة ماسية)",
                    timestamp = System.currentTimeMillis() - 3600000L * 24
                ),
                PointTransaction(
                    id = 5,
                    type = "CALL_SPENT",
                    amount = -75,
                    description = "مكالمة فيديو خاصة (3 دقائق)",
                    timestamp = System.currentTimeMillis() - 3600000L * 30
                ),
                PointTransaction(
                    id = 6,
                    type = "AGENCY_COMMISSION",
                    amount = 120,
                    description = "عمولة أرباح وكلاء فرعيين (إحالة نشطة)",
                    timestamp = System.currentTimeMillis() - 3600000L * 48
                ),
                PointTransaction(
                    id = 7,
                    type = "RANDOM_MATCH",
                    amount = -5,
                    description = "مطابقة عشوائية بفلترة الدولة (المملكة العربية السعودية)",
                    timestamp = System.currentTimeMillis() - 3600000L * 72
                )
            )
        } else {
            transactions
        }

        baseList.filter { item ->
            val matchesFilter = when (selectedFilter) {
                TransactionFilterType.ALL -> true
                TransactionFilterType.EARNINGS_ONLY -> item.amount > 0
                TransactionFilterType.EXPENDITURES_ONLY -> item.amount < 0
            }
            val matchesSearch = searchQuery.isBlank() ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.type.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }.sortedByDescending { it.timestamp }
    }

    val totalEarned = remember(transactions) {
        val list = if (transactions.isEmpty()) listOf(10, 35, 500, 120) else transactions.filter { it.amount > 0 }.map { it.amount }
        list.sum()
    }

    val totalSpent = remember(transactions) {
        val list = if (transactions.isEmpty()) listOf(50, 75, 5) else transactions.filter { it.amount < 0 }.map { -it.amount }
        list.sum()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
            .statusBarsPadding()
            .testTag("transaction_history_screen")
    ) {
        // App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(HuhTheme.colors.surfaceElevated, CircleShape)
                        .testTag("transaction_history_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = HuhTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "سجل المعاملات والنقاط 📜",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "سجل زمني مفصل لجميع الأرباح والمصروفات",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (!isRefreshing) {
                            coroutineScope.launch {
                                isRefreshing = true
                                delay(800)
                                isRefreshing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(HuhTheme.colors.surfaceElevated, CircleShape)
                        .testTag("refresh_transactions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = AyerCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = AyerGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = AyerGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${userProfile?.pointsBalance ?: 250}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AyerGold
                        )
                    }
                }
            }
        }

        if (isRefreshing) {
            TransactionHistorySkeletonLoader(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("transaction_shimmer_loader")
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Summary Stat Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Earned Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إجمالي الأرباح",
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "+$totalEarned 💎",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    // Total Spent Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFFF43F5E).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFFF43F5E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إجمالي الهدايا والإنفاق",
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "-$totalSpent 💎",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFF43F5E)
                            )
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في سجل المعاملات (هدية، مهمة، شحن...)", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = HuhTheme.colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HuhTheme.colors.surfaceElevated,
                        unfocusedContainerColor = HuhTheme.colors.surfaceElevated,
                        focusedBorderColor = HuhTheme.colors.primary,
                        unfocusedBorderColor = HuhTheme.colors.outline
                    ),
                    singleLine = true
                )
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == TransactionFilterType.ALL,
                        onClick = { selectedFilter = TransactionFilterType.ALL },
                        label = { Text("الكل (${displayTransactions.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HuhTheme.colors.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_all_transactions")
                    )

                    FilterChip(
                        selected = selectedFilter == TransactionFilterType.EARNINGS_ONLY,
                        onClick = { selectedFilter = TransactionFilterType.EARNINGS_ONLY },
                        label = { Text("الأرباح والمكافآت (+)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_earnings_transactions")
                    )

                    FilterChip(
                        selected = selectedFilter == TransactionFilterType.EXPENDITURES_ONLY,
                        onClick = { selectedFilter = TransactionFilterType.EXPENDITURES_ONLY },
                        label = { Text("المصروفات والهدايا (-)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF43F5E),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_expenditures_transactions")
                    )
                }
            }

            // Empty State
            if (displayTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = HuhTheme.colors.textTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد معاملات مطابقة للفلتر المحدد",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "سيتم تسجيل أي أرباح أو هدايا أو مهام مكتملة هنا فوراً",
                                fontSize = 12.sp,
                                color = HuhTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Transaction Cards List
            items(displayTransactions, key = { "${it.id}_${it.timestamp}" }) { tx ->
                TransactionCardItem(tx = tx)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
}

@Composable
fun TransactionCardItem(tx: PointTransaction) {
    val isEarning = tx.amount > 0
    val amountColor = if (isEarning) Color(0xFF10B981) else Color(0xFFF43F5E)
    val amountPrefix = if (isEarning) "+" else ""

    val iconPair = getTransactionIcon(tx.type)
    val formattedDate = remember(tx.timestamp) {
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale("ar"))
            sdf.format(Date(tx.timestamp))
        } catch (e: Exception) {
            "اليوم"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_${tx.id}"),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isEarning) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFF43F5E).copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconPair.second.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, iconPair.second.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconPair.first,
                        contentDescription = null,
                        tint = iconPair.second,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tx.description,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary,
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = HuhTheme.colors.surface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = getTransactionCategoryLabel(tx.type),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HuhTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = formattedDate,
                            fontSize = 10.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${tx.amount} 💎",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = amountColor
                )
                Text(
                    text = if (isEarning) "وارد للمحفظة" else "مصروف / إهداء",
                    fontSize = 10.sp,
                    color = HuhTheme.colors.textTertiary
                )
            }
        }
    }
}

fun getTransactionIcon(type: String): Pair<ImageVector, Color> {
    return when (type.uppercase()) {
        "GIFT_SENT", "GIFT" -> Pair(Icons.Default.CardGiftcard, Color(0xFFF43F5E))
        "PACKAGE_PURCHASE", "PURCHASE" -> Pair(Icons.Default.MonetizationOn, AyerGold)
        "MISSION_REWARD", "ENGAGEMENT_REWARD" -> Pair(Icons.Default.Stars, Color(0xFF10B981))
        "CALL_SPENT", "CALL" -> Pair(Icons.Default.Videocam, AyerCyan)
        "AGENCY_COMMISSION", "COMMISSION" -> Pair(Icons.Default.WorkspacePremium, AyerGold)
        "DAILY_FREE" -> Pair(Icons.Default.Stars, Color(0xFFF59E0B))
        else -> Pair(Icons.Default.ReceiptLong, AyerPrimary)
    }
}

fun getTransactionCategoryLabel(type: String): String {
    return when (type.uppercase()) {
        "GIFT_SENT", "GIFT" -> "إهداء في بث"
        "PACKAGE_PURCHASE", "PURCHASE" -> "شحن رصيد"
        "MISSION_REWARD" -> "مهمة يومية"
        "ENGAGEMENT_REWARD" -> "تفاعل يومي"
        "CALL_SPENT", "CALL" -> "مكالمة مدفوعة"
        "AGENCY_COMMISSION", "COMMISSION" -> "عمولة وكالة"
        "DAILY_FREE" -> "نقاط مجانية"
        "RANDOM_MATCH" -> "مطابقة عشوائية"
        else -> "معاملة رصيد"
    }
}
