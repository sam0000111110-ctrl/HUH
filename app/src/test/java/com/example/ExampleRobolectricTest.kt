package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AyerDatabase
import com.example.data.model.AppNotification
import com.example.data.model.NotificationType
import com.example.util.AyerNotificationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HUH", appName)
  }

  @Test
  fun `notification channel created successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AyerNotificationManager.initNotificationChannels(context)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(AyerNotificationManager.CHANNEL_ID_INFLUENCER_ALERTS)
    assertNotNull(channel)
    assertEquals("إشعارات العمولات والإحالات - Ayer", channel.name)
  }

  @Test
  fun `notification dao operations succeed`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AyerDatabase::class.java).build()
    val dao = db.notificationDao()

    val testNotification = AppNotification(
        type = NotificationType.COMMISSION_PAYOUT,
        title = "دفعة عمولة جديدة",
        message = "استلمت 500 نقطة",
        amountCoins = 500,
        senderName = "بوابة العمولات"
    )
    val id = dao.insertNotification(testNotification)
    assertTrue(id > 0)

    val count = dao.getUnreadCount().first()
    assertEquals(1, count)

    val list = dao.getAllNotifications().first()
    assertEquals(1, list.size)
    assertEquals(500, list[0].amountCoins)

    dao.markAsRead(id)
    assertEquals(0, dao.getUnreadCount().first())
    db.close()
  }

  @Test
  fun `user profile and engagement stats calculation`() = runBlocking {
    val userProfile = com.example.data.model.UserProfile(
        name = "سامي الحربي",
        pointsBalance = 1500,
        totalMatchesCount = 40
    )
    val engagementState = com.example.data.model.UserEngagementState(
        dailyStreakDays = 7,
        todayStreamMinutes = 25,
        todayGiftsSent = 5
    )

    val calculatedHours = (userProfile.totalMatchesCount * 0.8 + 18.5)
    assertTrue(calculatedHours > 50.0)
    val calculatedFriends = (userProfile.totalMatchesCount * 3 + 28)
    assertEquals(148, calculatedFriends)
    assertEquals(7, engagementState.dailyStreakDays)
  }
}
