/*
 * Copyright (C) 2018 The Android Open Source Project
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
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

package com.android.internal.telephony;

import android.app.PendingIntent;
import android.net.Uri;
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
import android.os.Bundle;

// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
import java.util.List;

// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
/**
 * Base class for ISms that facilitates forward compatibility with new features.
 */
public class ISmsImplBase extends ISms.Stub {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.

    @Override
    public List<SmsRawData> getAllMessagesFromIccEfForSubscriber(int subId, String callingPkg) {
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public boolean updateMessageOnIccEfForSubscriber(int subId, String callingPkg, int messageIndex,
            int newStatus, byte[] pdu) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean copyMessageToIccEfForSubscriber(int subId, String callingPkg, int status,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            byte[] pdu, byte[] smsc) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public void sendDataForSubscriber(int subId, String callingPkg, String callingAttributionTag,
            String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public void sendTextForSubscriber(int subId, String callingPkg, String callingAttributionTag,
            String destAddr, String scAddr, String text, PendingIntent sentIntent,
            PendingIntent deliveryIntent, boolean persistMessageForNonDefaultSmsApp,
            long messageId) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public void sendTextForSubscriberWithOptions(int subId, String callingPkg,
            String callingAttributionTag, String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent,
            boolean persistMessageForNonDefaultSmsApp, int priority, boolean expectMore,
            int validityPeriod) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public void injectSmsPduForSubscriber(
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            int subId, byte[] pdu, String format, PendingIntent receivedIntent) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendMultipartTextForSubscriber(int subId, String callingPkg,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            String callingAttributionTag, String destinationAddress, String scAddress,
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            List<String> parts, List<PendingIntent> sentIntents,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            List<PendingIntent> deliveryIntents, boolean persistMessageForNonDefaultSmsApp,
            long messageId) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendMultipartTextForSubscriberWithOptions(int subId, String callingPkg,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            String callingAttributionTag, String destinationAddress, String scAddress,
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, boolean persistMessageForNonDefaultSmsApp,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            int priority, boolean expectMore, int validityPeriod) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public boolean enableCellBroadcastForSubscriber(int subId, int messageIdentifier, int ranType) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public boolean disableCellBroadcastForSubscriber(int subId, int messageIdentifier,
            int ranType) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean enableCellBroadcastRangeForSubscriber(int subId, int startMessageId,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            int endMessageId, int ranType) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean disableCellBroadcastRangeForSubscriber(int subId, int startMessageId,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            int endMessageId, int ranType) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public int getPremiumSmsPermission(String packageName) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public int getPremiumSmsPermissionForSubscriber(int subId, String packageName) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public void setPremiumSmsPermission(String packageName, int permission) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPremiumSmsPermissionForSubscriber(int subId, String packageName,
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
            int permission) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public boolean isImsSmsSupportedForSubscriber(int subId) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public boolean isSmsSimPickActivityNeeded(int subId) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public int getPreferredSmsSubscription() {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public String getImsSmsFormatForSubscriber(int subId) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public boolean isSMSPromptEnabled() {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public void sendStoredText(int subId, String callingPkg, String callingAttributionTag,
            Uri messageUri, String scAddress, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public void sendStoredMultipartText(int subId, String callingPkg, String callingAttributionTag,
            Uri messageUri, String scAddress, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }

// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    @Override
    public Bundle getCarrierConfigValuesForSubscriber(int subId) {
        throw new UnsupportedOperationException();
    }

// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    @Override
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
    public String createAppSpecificSmsToken(int subId, String callingPkg, PendingIntent intent) {
// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
        throw new UnsupportedOperationException();
    }
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.

    @Override
    public String createAppSpecificSmsTokenWithPackageInfo(
            int subId, String callingPkg, String prefixes, PendingIntent intent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStorageMonitorMemoryStatusOverride(int subId, boolean storageAvailable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearStorageMonitorMemoryStatusOverride(int subId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int checkSmsShortCodeDestination(int subid, String callingPackage,
            String callingFeatureId, String destAddress, String countryIso) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSmscAddressFromIccEfForSubscriber(int subId, String callingPackage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setSmscAddressOnIccEfForSubscriber(
            String smsc, int subId, String callingPackage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSmsCapacityOnIccForSubscriber(int subId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean resetAllCellBroadcastRanges(int subId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getWapMessageSize(String locationUrl) {
        throw new UnsupportedOperationException();
    }

// QTI_BEGIN: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
}
// QTI_END: 2018-06-26: Telephony: Introduce a base class for ISMS.aidl.
