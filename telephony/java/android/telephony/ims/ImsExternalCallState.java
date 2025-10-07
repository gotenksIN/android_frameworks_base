// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
/*
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
 * Copyright (C) 2018 The Android Open Source Project
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
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
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
 * limitations under the License
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
 */

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
package android.telephony.ims;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint

import com.android.telephony.Rlog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint

/**
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
 * Parcelable object to handle MultiEndpoint Dialog Event Package Information.
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
 * @hide
 */
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
@SystemApi
public final class ImsExternalCallState implements Parcelable {
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint

    private static final String TAG = "ImsExternalCallState";

    // Dialog States
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    /**
     * The external call is in the confirmed dialog state.
     */
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public static final int CALL_STATE_CONFIRMED = 1;
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    /**
     * The external call is in the terminated dialog state.
     */
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public static final int CALL_STATE_TERMINATED = 2;
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint

    /**@hide*/
    @IntDef(value = {
                    CALL_STATE_CONFIRMED,
                    CALL_STATE_TERMINATED
            },
            prefix = "CALL_STATE_")
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExternalCallState {}

    /**@hide*/
    @IntDef(value = {
                    ImsCallProfile.CALL_TYPE_VOICE,
                    ImsCallProfile.CALL_TYPE_VT_TX,
                    ImsCallProfile.CALL_TYPE_VT_RX,
                    ImsCallProfile.CALL_TYPE_VT
            },
            prefix = "CALL_TYPE_")
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExternalCallType {}



// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    // Dialog Id
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    private int mCallId;
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    // Number
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    private Uri mAddress;
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    private Uri mLocalAddress;
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    private boolean mIsPullable;
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    // CALL_STATE_CONFIRMED / CALL_STATE_TERMINATED
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    private int mCallState;
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    // ImsCallProfile#CALL_TYPE_*
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    private int mCallType;
    private boolean mIsHeld;
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint

    /** @hide */
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public ImsExternalCallState() {
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    /**@hide*/
    public ImsExternalCallState(int callId, Uri address, boolean isPullable,
            @ExternalCallState int callState, int callType, boolean isCallheld) {
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
        mCallId = callId;
        mAddress = address;
        mIsPullable = isPullable;
        mCallState = callState;
        mCallType = callType;
        mIsHeld = isCallheld;
        Rlog.d(TAG, "ImsExternalCallState = " + this);
    }

// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
    /**@hide*/
    public ImsExternalCallState(int callId, Uri address, Uri localAddress,
            boolean isPullable, @ExternalCallState int callState, int callType,
            boolean isCallheld) {
        mCallId = callId;
        mAddress = address;
        mLocalAddress = localAddress;
        mIsPullable = isPullable;
        mCallState = callState;
        mCallType = callType;
        mIsHeld = isCallheld;
        Rlog.d(TAG, "ImsExternalCallState = " + this);
    }

    /**
     * Create a new ImsExternalCallState instance to contain Multiendpoint Dialog information.
     * @param callId The unique ID of the call, which will be used to identify this external
     *               connection.
     * @param address A {@link Uri} containing the remote address of this external connection.
     * @param localAddress A {@link Uri} containing the local address information.
     * @param isPullable A flag determining if this external connection can be pulled to the current
     *         device.
     * @param callState The state of the external call.
     * @param callType The type of external call.
     * @param isCallheld A flag determining if the external connection is currently held.
     */
    public ImsExternalCallState(@NonNull String callId, @NonNull Uri address,
            @Nullable Uri localAddress, boolean isPullable, @ExternalCallState int callState,
            @ExternalCallType int callType, boolean isCallheld) {
        mCallId = getIdForString(callId);
        mAddress = address;
        mLocalAddress = localAddress;
        mIsPullable = isPullable;
        mCallState = callState;
        mCallType = callType;
        mIsHeld = isCallheld;
        Rlog.d(TAG, "ImsExternalCallState = " + this);
    }

    /** @hide */
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public ImsExternalCallState(Parcel in) {
        mCallId = in.readInt();
        ClassLoader classLoader = ImsExternalCallState.class.getClassLoader();
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        mAddress = in.readParcelable(classLoader, android.net.Uri.class);
        mLocalAddress = in.readParcelable(classLoader, android.net.Uri.class);
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        mIsPullable = (in.readInt() != 0);
        mCallState = in.readInt();
        mCallType = in.readInt();
        mIsHeld = (in.readInt() != 0);
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
        Rlog.d(TAG, "ImsExternalCallState const = " + this);
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCallId);
        out.writeParcelable(mAddress, 0);
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        out.writeParcelable(mLocalAddress, 0);
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        out.writeInt(mIsPullable ? 1 : 0);
        out.writeInt(mCallState);
        out.writeInt(mCallType);
        out.writeInt(mIsHeld ? 1 : 0);
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
        Rlog.d(TAG, "ImsExternalCallState writeToParcel = " + out.toString());
// QTI_END: 2016-03-23: Telephony: IMS: Changes for MultiEndpoint
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public static final @android.annotation.NonNull Parcelable.Creator<ImsExternalCallState> CREATOR =
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
            new Parcelable.Creator<ImsExternalCallState>() {
        @Override
        public ImsExternalCallState createFromParcel(Parcel in) {
            return new ImsExternalCallState(in);
        }

        @Override
        public ImsExternalCallState[] newArray(int size) {
            return new ImsExternalCallState[size];
        }
    };

    public int getCallId() {
        return mCallId;
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public @NonNull Uri getAddress() {
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        return mAddress;
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    /**
     * @return A {@link Uri} containing the local address from the Multiendpoint Dialog Information.
     */
    public @Nullable Uri getLocalAddress() {
        return mLocalAddress;
    }

// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public boolean isCallPullable() {
        return mIsPullable;
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public @ExternalCallState int getCallState() {
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        return mCallState;
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    public @ExternalCallType int getCallType() {
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
        return mCallType;
    }

    public boolean isCallHeld() {
        return mIsHeld;
    }

// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    @NonNull
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
    @Override
    public String toString() {
        return "ImsExternalCallState { mCallId = " + mCallId +
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
                ", mAddress = " + Rlog.pii(TAG, mAddress) +
                ", mLocalAddress = " + Rlog.pii(TAG, mLocalAddress) +
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
                ", mIsPullable = " + mIsPullable +
                ", mCallState = " + mCallState +
                ", mCallType = " + mCallType +
                ", mIsHeld = " + mIsHeld + "}";
    }
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint

    private int getIdForString(String idString) {
        try {
            return Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            // In the case that there are alphanumeric characters, we will create a hash of the
            // String value as a backup.
            // TODO: Modify call IDs to use Strings as keys instead of integers in telephony/telecom
            return idString.hashCode();
        }
    }
// QTI_BEGIN: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
}
// QTI_END: 2016-03-11: Telephony: IMS: Changes for MultiEndpoint
