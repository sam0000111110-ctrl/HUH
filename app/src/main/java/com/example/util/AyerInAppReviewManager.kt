package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Manages Google Play In-App Review API interactions and fallback store launches.
 * Triggers seamlessly when users achieve mission milestones or request app rating.
 */
object AyerInAppReviewManager {
    private const val TAG = "AyerInAppReview"

    private var reviewManager: ReviewManager? = null
    private var preWarmReviewInfo: ReviewInfo? = null

    fun init(context: Context) {
        try {
            reviewManager = ReviewManagerFactory.create(context.applicationContext)
            preWarmReview(context)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to initialize ReviewManager (likely non-GMS environment): ${e.message}")
        }
    }

    /**
     * Pre-fetches the ReviewInfo object ahead of time for instant dialog rendering.
     */
    fun preWarmReview(context: Context) {
        try {
            val manager = reviewManager ?: ReviewManagerFactory.create(context.applicationContext).also {
                reviewManager = it
            }
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    preWarmReviewInfo = task.result
                    Log.d(TAG, "In-App ReviewInfo pre-warmed successfully")
                } else {
                    Log.w(TAG, "Failed to pre-warm In-App ReviewInfo: ${task.exception?.message}")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Exception during preWarmReview: ${e.message}")
        }
    }

    /**
     * Launches the official Google Play In-App Review dialog flow on the given Activity.
     * Falls back to launching the Play Store product page if in-app review is unavailable.
     */
    fun launchReviewFlow(activity: Activity, onComplete: () -> Unit = {}) {
        val manager = reviewManager ?: ReviewManagerFactory.create(activity).also { reviewManager = it }
        val cachedInfo = preWarmReviewInfo

        if (cachedInfo != null) {
            val flow = manager.launchReviewFlow(activity, cachedInfo)
            flow.addOnCompleteListener { _ ->
                Log.d(TAG, "Google Play In-App review flow finished")
                preWarmReviewInfo = null
                onComplete()
            }
        } else {
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { _ ->
                        Log.d(TAG, "Google Play In-App review flow completed successfully")
                        onComplete()
                    }
                } else {
                    Log.w(TAG, "Review flow request failed, launching Play Store fallback")
                    openPlayStoreFallback(activity)
                    onComplete()
                }
            }
        }
    }

    /**
     * Fallback to direct Play Store market intent.
     */
    fun openPlayStoreFallback(context: Context) {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
