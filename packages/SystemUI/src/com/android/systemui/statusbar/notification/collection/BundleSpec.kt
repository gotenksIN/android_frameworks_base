/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.notification.collection

import android.app.NotificationChannel
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.Adjustment
import android.service.notification.DynamicBundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.internal.R
import com.android.systemui.statusbar.notification.stack.BUCKET_DYNAMIC_BUNDLE
import com.android.systemui.statusbar.notification.stack.BUCKET_NEWS
import com.android.systemui.statusbar.notification.stack.BUCKET_PROMO
import com.android.systemui.statusbar.notification.stack.BUCKET_RECS
import com.android.systemui.statusbar.notification.stack.BUCKET_SOCIAL

/** Represents the title of a bundle. */
sealed interface BundleTitle {
    /** Can be invoked inside a composable function to get the bundle title. */
    @Composable fun getComposableText(): String

    /** Can be invoked outside a composable function to get the bundle title. */
    fun getText(context: Context): String

    /** The bundle title is a string resource. */
    data class StringResource(@StringRes val textResId: Int) : BundleTitle {
        @Composable
        override fun getComposableText(): String {
            return stringResource(textResId)
        }

        override fun getText(context: Context): String {
            return context.getString(textResId)
        }
    }

    /** The bundle title is a custom, already-loaded string. */
    data class LoadedString(val text: String) : BundleTitle {
        @Composable
        override fun getComposableText(): String {
            return text
        }

        override fun getText(context: Context): String {
            return text
        }
    }
}

/** Represents the icon for a bundle. */
sealed interface BundleIcon {
    /** Returns the [Icon] version of the bundle icon. */
    fun getIcon(context: Context): Icon

    /** The bundle icon is a drawable resource. */
    data class DrawableResource(@DrawableRes val iconResId: Int) : BundleIcon {
        override fun getIcon(context: Context): Icon {
            return Icon.createWithResource(context, iconResId)
        }
    }

    /** The bundle icon is a custom emoji. */
    data class Emoji(val emoji: String) : BundleIcon {
        override fun getIcon(context: Context): Icon {
            // TODO: b/478225883 - Render emoji icon correctly everywhere.
            return Icon.createWithResource(context, R.drawable.ic_info)
        }
    }
}

data class BundleSpec(
    val key: String,
    val titleText: BundleTitle,
    @StringRes val summaryTextRes: Int = 0,
    val summaryText: String? = null,
    val icon: BundleIcon,
    val bucket: Int,

    /**
     * This is the id / [type] that identifies the bundle when calling APIs of
     * [android.app.INotificationManager]
     */
    val bundleType: Int,
) {
    companion object {
        val PROMOTIONS =
            BundleSpec(
                key = NotificationChannel.PROMOTIONS_ID,
                titleText =
                    BundleTitle.StringResource(R.string.promotional_notification_channel_label),
                summaryTextRes =
                    com.android.systemui.res.R.string.notification_guts_promotions_summary,
                icon =
                    BundleIcon.DrawableResource(com.android.settingslib.R.drawable.ic_promotions),
                bucket = BUCKET_PROMO,
                bundleType = Adjustment.TYPE_PROMOTION,
            )
        val SOCIAL_MEDIA =
            BundleSpec(
                key = NotificationChannel.SOCIAL_MEDIA_ID,
                titleText = BundleTitle.StringResource(R.string.social_notification_channel_label),
                summaryTextRes = com.android.systemui.res.R.string.notification_guts_social_summary,
                icon = BundleIcon.DrawableResource(com.android.settingslib.R.drawable.ic_social),
                bucket = BUCKET_SOCIAL,
                bundleType = Adjustment.TYPE_SOCIAL_MEDIA,
            )
        val NEWS =
            BundleSpec(
                key = NotificationChannel.NEWS_ID,
                titleText = BundleTitle.StringResource(R.string.news_notification_channel_label),
                summaryTextRes = com.android.systemui.res.R.string.notification_guts_news_summary,
                icon = BundleIcon.DrawableResource(com.android.settingslib.R.drawable.ic_news),
                bucket = BUCKET_NEWS,
                bundleType = Adjustment.TYPE_NEWS,
            )
        val RECOMMENDED =
            BundleSpec(
                key = NotificationChannel.RECS_ID,
                titleText = BundleTitle.StringResource(R.string.recs_notification_channel_label),
                summaryTextRes = com.android.systemui.res.R.string.notification_guts_recs_summary,
                icon = BundleIcon.DrawableResource(com.android.settingslib.R.drawable.ic_recs),
                bucket = BUCKET_RECS,
                bundleType = Adjustment.TYPE_CONTENT_RECOMMENDATION,
            )

        fun fromDynamicBundle(dynamicBundle: DynamicBundle): BundleSpec {
            return BundleSpec(
                key =
                    NotificationChannel.getChannelIdForBundleType(
                        dynamicBundle.dynamicBundleType
                    )!!,
                titleText =
                    BundleTitle.StringResource(R.string.promotional_notification_channel_label),
                summaryText = dynamicBundle.bundleName,
                icon =
                    BundleIcon.DrawableResource(
                        com.android.settingslib.R.drawable.ic_dynamic_bundle
                    ),
                bucket = BUCKET_DYNAMIC_BUNDLE,
                bundleType = dynamicBundle.dynamicBundleType,
            )
        }
    }
}
