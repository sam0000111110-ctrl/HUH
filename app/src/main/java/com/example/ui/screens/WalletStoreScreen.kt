package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CoinPackage
import com.example.data.model.PointTransaction
import com.example.ui.AyerViewModel
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerGoldGlow
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant
import com.example.ui.theme.HuhTheme
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.SecurityUtils
import java.util.Locale

@Composable
fun WalletStoreScreen(
    viewModel: AyerViewModel,
    balance: Int,
    transactions: List<PointTransaction>,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val activity = com.example.util.SecurityUtils.findActivity(context)
        com.example.util.SecurityUtils.setScreenCaptureProtection(activity, true)
        onDispose {
            com.example.util.SecurityUtils.setScreenCaptureProtection(activity, false)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.initPlayBilling()
    }
    var showFullHistory by remember { mutableStateOf(false) }

    if (showFullHistory) {
        TransactionHistoryScreen(
            viewModel = viewModel,
            onBack = { showFullHistory = false }
        )
        return
    }

    val packages = viewModel.getCoinPackages()
    val playPrices by viewModel.playProductPrices.collectAsStateWithLifecycle()
    val isBillingReady by viewModel.isPlayBillingReady.collectAsStateWithLifecycle()
    val activity = remember(context) { SecurityUtils.findActivity(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Title
        item {
            Text(
                text = "المحفظة ومتجر النقاط 🪙",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )
            Text(
                text = "اشحن نقاطك للمطابقة المخصصة وإرسال الهدايا للمضيفين",
                fontSize = 12.sp,
                color = HuhTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Current Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = HuhTheme.colors.surfaceElevated
                ),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(HuhTheme.colors.surfaceElevated, HuhTheme.colors.surfaceVariant)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "رصيدك الحالي",
                                fontSize = 13.sp,
                                color = HuhTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$balance",
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = HuhTheme.colors.gold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "نقطة / Coin",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuhTheme.colors.textPrimary
                                )
                            }
                            Text(
                                text = "≈ $${String.format(Locale.US, "%.2f", balance / 250.0)} دولار أمريكي",
                                fontSize = 11.sp,
                                color = HuhTheme.colors.textMuted
                            )
                        }

                        Image(
                            painter = painterResource(id = R.drawable.coin_badge),
                            contentDescription = "Coin Badge",
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Daily Free Coins Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A1F)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF00E676).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "هدية الدخول اليومي (+10 نقاط)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "مجاناً كل 24 ساعة لتشجيع التواصل",
                                fontSize = 11.sp,
                                color = Color(0xFFB9F6CA)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.claimDailyBonus() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("claim_daily_button")
                    ) {
                        Text(text = "استلام 🎁", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section Title: Coin Packages
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "باقات شحن النقاط (Google Play)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textPrimary
                )
                Surface(
                    color = if (isBillingReady) Color(0xFF00C853).copy(alpha = 0.16f) else Color.Gray.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isBillingReady) "Google Play متصل ✓" else "بانتظار Google Play",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBillingReady) Color(0xFF00E676) else HuhTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // List of Coin Packages
        items(packages) { pkg ->
            CoinPackageRow(
                pkg = pkg,
                localizedPrice = playPrices[pkg.productId],
                onPurchase = { viewModel.purchasePackage(pkg, activity) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Transaction History Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = HuhTheme.colors.gold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "سجل المعاملات والنقاط",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HuhTheme.colors.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showFullHistory = true }
                        .testTag("btn_view_full_history")
                ) {
                    Text(
                        text = "عرض الكل ➔",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    text = "لا توجد معاملات سابقة حتى الآن",
                    fontSize = 12.sp,
                    color = HuhTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(transactions.take(5)) { tx ->
                TransactionRow(transaction = tx)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CoinPackageRow(
    pkg: CoinPackage,
    localizedPrice: String? = null,
    onPurchase: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPurchase)
            .testTag("package_${pkg.id}"),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = if (pkg.isPopular) androidx.compose.foundation.BorderStroke(1.5.dp, HuhTheme.colors.gold) else androidx.compose.foundation.BorderStroke(0.5.dp, HuhTheme.colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(HuhTheme.colors.gold.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🪙", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${pkg.coins} نقطة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        if (pkg.bonusCoins > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = HuhTheme.colors.secondary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "+${pkg.bonusCoins} مجاناً",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (pkg.tag != null) {
                        Text(
                            text = pkg.tag,
                            fontSize = 11.sp,
                            color = HuhTheme.colors.gold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Button(
                onClick = onPurchase,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pkg.isPopular) HuhTheme.colors.gold else HuhTheme.colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = localizedPrice ?: "$${pkg.priceUsd}",
                    fontWeight = FontWeight.Bold,
                    color = if (pkg.isPopular) Color.Black else Color.White
                )
            }
        }
    }
}

@Composable
fun TransactionRow(transaction: PointTransaction) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    val isPositive = transaction.amount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HuhTheme.colors.textPrimary
            )
            Text(
                text = dateFormat.format(Date(transaction.timestamp)),
                fontSize = 11.sp,
                color = HuhTheme.colors.textSecondary
            )
        }

        Text(
            text = "${if (isPositive) "+" else ""}${transaction.amount} 🪙",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) Color(0xFF00E676) else Color(0xFFFF5252)
        )
    }
    HorizontalDivider(color = HuhTheme.colors.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
}
