/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server;

import static com.android.server.input.InputManagerService.SW_HEADPHONE_INSERT;
import static com.android.server.input.InputManagerService.SW_HEADPHONE_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_LINEOUT_INSERT;
import static com.android.server.input.InputManagerService.SW_LINEOUT_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_MICROPHONE_INSERT;
import static com.android.server.input.InputManagerService.SW_MICROPHONE_INSERT_BIT;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UEventObserver;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.view.InputDevice;

import com.android.internal.R;
import com.android.server.input.InputManagerService;
import com.android.server.input.InputManagerService.WiredAccessoryCallbacks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
import java.util.HashMap;
import java.util.Map;
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections

/**
 * <p>WiredAccessoryManager monitors for a wired headset on the main board or dock using
 * both the InputManagerService notifyWiredAccessoryChanged interface and the UEventObserver
 * subsystem.
 */
final class WiredAccessoryManager implements WiredAccessoryCallbacks {
    private static final String TAG = WiredAccessoryManager.class.getSimpleName();
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
    private static final boolean LOG = true;
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections

    private static final int BIT_HEADSET = (1 << 0);
    private static final int BIT_HEADSET_NO_MIC = (1 << 1);
    private static final int BIT_USB_HEADSET_ANLG = (1 << 2);
    private static final int BIT_USB_HEADSET_DGTL = (1 << 3);
    private static final int BIT_HDMI_AUDIO = (1 << 4);
    private static final int BIT_LINEOUT = (1 << 5);
    private static final int SUPPORTED_HEADSETS = (BIT_HEADSET | BIT_HEADSET_NO_MIC |
            BIT_USB_HEADSET_ANLG | BIT_USB_HEADSET_DGTL |
            BIT_HDMI_AUDIO | BIT_LINEOUT);

    private static final String NAME_H2W = "h2w";
    private static final String NAME_USB_AUDIO = "usb_audio";
    private static final String NAME_HDMI_AUDIO = "hdmi_audio";
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
    private static final String NAME_DP_AUDIO = "soc:qcom,msm-ext-disp";
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
    // within a device, a single stream supports DP
    private static final String[] DP_AUDIO_CONNS = {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                                                     NAME_DP_AUDIO + "/3/1",
                                                     NAME_DP_AUDIO + "/2/1",
                                                     NAME_DP_AUDIO + "/1/1",
                                                     NAME_DP_AUDIO + "/0/1",
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2021-06-17: Audio: WiredAccessoryManager: Update display port device index
                                                     NAME_DP_AUDIO + "/3/0",
                                                     NAME_DP_AUDIO + "/2/0",
// QTI_END: 2021-06-17: Audio: WiredAccessoryManager: Update display port device index
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                                                     NAME_DP_AUDIO + "/1/0",
                                                     NAME_DP_AUDIO + "/0/0"
                                                   };
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices

// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
    private static final String NAME_HDMI = "hdmi";
    private static final String INTF_DP = "DP";
    private static final String INTF_HDMI = "HDMI";
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
    private static final int MSG_NEW_DEVICE_STATE = 1;
    private static final int MSG_SYSTEM_READY = 2;

    private final Object mLock = new Object();

    private final WakeLock mWakeLock;  // held while there is a pending route change
    private final AudioManager mAudioManager;

    private int mHeadsetState;
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
    private int mDpCount;
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
    private String mDetectedIntf = INTF_DP;
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
    private int mSwitchValues;

    private final WiredAccessoryObserver mObserver;
    private final WiredAccessoryExtconObserver mExtconObserver;
    private final InputManagerService mInputManager;

    private final boolean mUseDevInputEventForAudioJack;

// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
    private static final int MAX_DP_COUNT = 2;
    private boolean []streamsInUse = new boolean[MAX_DP_COUNT];
    private Map<String, Integer > streamIndexMap = new HashMap();

// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
    public WiredAccessoryManager(Context context, InputManagerService inputManager) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiredAccessoryManager");
        mWakeLock.setReferenceCounted(false);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mInputManager = inputManager;

        mUseDevInputEventForAudioJack =
                context.getResources().getBoolean(R.bool.config_useDevInputEventForAudioJack);

        mExtconObserver = new WiredAccessoryExtconObserver();
        mObserver = new WiredAccessoryObserver();
    }

    private void onSystemReady() {
        if (mUseDevInputEventForAudioJack) {
            int switchValues = 0;
            if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY, SW_HEADPHONE_INSERT)
                    == 1) {
                switchValues |= SW_HEADPHONE_INSERT_BIT;
            }
            if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY, SW_MICROPHONE_INSERT)
                    == 1) {
                switchValues |= SW_MICROPHONE_INSERT_BIT;
            }
            if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY, SW_LINEOUT_INSERT) == 1) {
                switchValues |= SW_LINEOUT_INSERT_BIT;
            }
            notifyWiredAccessoryChanged(
                    0,
                    switchValues,
                    SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT | SW_LINEOUT_INSERT_BIT,
                    true /*isSynchronous*/);
        }


// QTI_BEGIN: 2022-03-29: Audio: Force using WiredAccessoryObserver for DisplayPort
        if (ExtconUEventObserver.extconExists() && mExtconObserver.uEventCount() > 0 && false) {
// QTI_END: 2022-03-29: Audio: Force using WiredAccessoryObserver for DisplayPort
            if (mUseDevInputEventForAudioJack) {
                Log.w(TAG, "Both input event and extcon are used for audio jack,"
                        + " please just choose one.");
            }
            mExtconObserver.init();
        } else {
            mObserver.init();
        }
    }

    @Override
    public void notifyWiredAccessoryChanged(
            long whenNanos, int switchValues, int switchMask) {
        notifyWiredAccessoryChanged(whenNanos, switchValues, switchMask, false /*isSynchronous*/);
    }

    public void notifyWiredAccessoryChanged(
            long whenNanos, int switchValues, int switchMask, boolean isSynchronous) {
        if (LOG) {
            Slog.v(TAG, "notifyWiredAccessoryChanged: when=" + whenNanos
                    + " bits=" + switchCodeToString(switchValues, switchMask)
                    + " mask=" + Integer.toHexString(switchMask));
        }

        synchronized (mLock) {
            int headset;
            mSwitchValues = (mSwitchValues & ~switchMask) | switchValues;
            switch (mSwitchValues &
                    (SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT | SW_LINEOUT_INSERT_BIT)) {
                case 0:
                    headset = 0;
                    break;

                case SW_HEADPHONE_INSERT_BIT:
                    headset = BIT_HEADSET_NO_MIC;
                    break;

                case SW_LINEOUT_INSERT_BIT:
                    headset = BIT_LINEOUT;
                    break;

                case SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT:
                    headset = BIT_HEADSET;
                    break;

                case SW_MICROPHONE_INSERT_BIT:
                    headset = BIT_HEADSET;
                    break;

                default:
                    headset = 0;
                    break;
            }

            updateLocked(
                    NAME_H2W,
                    "",
                    (mHeadsetState & ~(BIT_HEADSET | BIT_HEADSET_NO_MIC | BIT_LINEOUT)) | headset,
                    isSynchronous);
        }
    }

    @Override
    public void systemReady() {
        synchronized (mLock) {
            mWakeLock.acquire();

            Message msg = mHandler.obtainMessage(MSG_SYSTEM_READY, 0, 0, null);
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Compare the existing headset state with the new state and pass along accordingly. Note
     * that this only supports a single headset at a time. Inserting both a usb and jacked headset
     * results in support for the last one plugged in. Similarly, unplugging either is seen as
     * unplugging all.
     *
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
     * For Display port allow upto two connections.
     * Block display port request if HDMI already connected and vice versa.
     *
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
     * @param newName  One of the NAME_xxx variables defined above.
     * @param newState 0 or one of the BIT_xxx variables defined above.
     * @param isSynchronous boolean to determine whether should happen sync or async
     */
    private void updateLocked(String newName, String address, int newState, boolean isSynchronous) {
        // Retain only relevant bits
        int headsetState = newState & SUPPORTED_HEADSETS;
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        int newDpState = newState & BIT_HDMI_AUDIO;
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        int usb_headset_anlg = headsetState & BIT_USB_HEADSET_ANLG;
        int usb_headset_dgtl = headsetState & BIT_USB_HEADSET_DGTL;
        int h2w_headset = headsetState & (BIT_HEADSET | BIT_HEADSET_NO_MIC | BIT_LINEOUT);
        boolean h2wStateChange = true;
        boolean usbStateChange = true;
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        boolean dpBitState = (mHeadsetState & BIT_HDMI_AUDIO) > 0 ? true: false;
        boolean dpCountState = (mDpCount == 0) ? false: true;

// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        if (LOG) {
            Slog.v(TAG, "newName=" + newName
                    + " newState=" + newState
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                    + " address=" + address
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                    + " headsetState=" + headsetState
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
                    + " prev headsetState=" + mHeadsetState
                    + " num of active dp conns= " + mDpCount);
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        }

// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        if (mHeadsetState == headsetState && !newName.startsWith(NAME_DP_AUDIO)) {
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
            Log.e(TAG, "No state change.");
            return;
        }

        // reject all suspect transitions: only accept state changes from:
        // - a: 0 headset to 1 headset
        // - b: 1 headset to 0 headset
        if (h2w_headset == (BIT_HEADSET | BIT_HEADSET_NO_MIC | BIT_LINEOUT)) {
            Log.e(TAG, "Invalid combination, unsetting h2w flag");
            h2wStateChange = false;
        }
        // - c: 0 usb headset to 1 usb headset
        // - d: 1 usb headset to 0 usb headset
        if (usb_headset_anlg == BIT_USB_HEADSET_ANLG && usb_headset_dgtl == BIT_USB_HEADSET_DGTL) {
            Log.e(TAG, "Invalid combination, unsetting usb flag");
            usbStateChange = false;
        }
        if (!h2wStateChange && !usbStateChange) {
            Log.e(TAG, "invalid transition, returning ...");
            return;
        }

// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        if (newName.startsWith(NAME_DP_AUDIO)) {
            if ((newDpState > 0) && (mDpCount < DP_AUDIO_CONNS.length)
                        && (dpBitState == dpCountState)) {
                // Allow DP0 if no HDMI previously connected.
                // Allow second request only if DP connected previously.
                mDpCount++;
            } else if ((newDpState == 0) && (mDpCount > 0)){
                mDpCount--;
            } else {
                Log.e(TAG, "No state change for DP.");
                return;
            }
        }

// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        mWakeLock.acquire();

        Log.i(TAG, "MSG_NEW_DEVICE_STATE");

// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
        // send a combined name, address string separated by |
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        if (newName.startsWith(NAME_DP_AUDIO)) {
            int pseudoHeadsetState = mHeadsetState;
            if (dpBitState && (newDpState != 0)) {
                // One DP already connected, so allow request to connect second.
                pseudoHeadsetState = mHeadsetState & (~BIT_HDMI_AUDIO);
            }
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
            Message msg = mHandler.obtainMessage(MSG_NEW_DEVICE_STATE, headsetState,
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
                                         pseudoHeadsetState,
                                         NAME_DP_AUDIO+"/"+address);
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
            mHandler.sendMessage(msg);
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports

            if ((headsetState == 0) && (mDpCount != 0)) {
                // Atleast one DP is connected, so keep mHeadsetState's DP bit set.
                headsetState = headsetState | BIT_HDMI_AUDIO;
            }
        } else {
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
            if (isSynchronous) {
// QTI_BEGIN: 2024-11-06: Audio: base: Add device name for wired connected devices
              setDevicesState(headsetState, mHeadsetState, newName+"/"+address);
// QTI_END: 2024-11-06: Audio: base: Add device name for wired connected devices
            } else {
              Message msg = mHandler.obtainMessage(MSG_NEW_DEVICE_STATE, headsetState,
                                           mHeadsetState,
                                           newName+"/"+address);
              mHandler.sendMessage(msg);
            }
// QTI_BEGIN: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports
        }
// QTI_END: 2019-08-06: Audio: WiredAccessoryManager: support for multiple display ports

        mHeadsetState = headsetState;
    }

    private final Handler mHandler = new Handler(Looper.myLooper(), null, true) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_NEW_DEVICE_STATE:
                    setDevicesState(msg.arg1, msg.arg2, (String) msg.obj);
                    mWakeLock.release();
                    break;
                case MSG_SYSTEM_READY:
                    onSystemReady();
                    mWakeLock.release();
                    break;
            }
        }
    };

    private void setDevicesState(
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            int headsetState, int prevHeadsetState, String headsetNameAddr) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
        synchronized (mLock) {
            int allHeadsets = SUPPORTED_HEADSETS;
            for (int curHeadset = 1; allHeadsets != 0; curHeadset <<= 1) {
                if ((curHeadset & allHeadsets) != 0) {
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                    setDeviceStateLocked(curHeadset, headsetState, prevHeadsetState,
                                         headsetNameAddr);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                    allHeadsets &= ~curHeadset;
                }
            }
        }
    }

    private void setDeviceStateLocked(int headset,
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            int headsetState, int prevHeadsetState, String headsetNameAddr) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
        if ((headsetState & headset) != (prevHeadsetState & headset)) {
            int outDevice = 0;
            int inDevice = 0;
            int state;

            if ((headsetState & headset) != 0) {
                state = 1;
            } else {
                state = 0;
            }

            if (headset == BIT_HEADSET) {
                outDevice = AudioManager.DEVICE_OUT_WIRED_HEADSET;
                inDevice = AudioManager.DEVICE_IN_WIRED_HEADSET;
            } else if (headset == BIT_HEADSET_NO_MIC) {
                outDevice = AudioManager.DEVICE_OUT_WIRED_HEADPHONE;
            } else if (headset == BIT_LINEOUT) {
                outDevice = AudioManager.DEVICE_OUT_LINE;
            } else if (headset == BIT_USB_HEADSET_ANLG) {
                outDevice = AudioManager.DEVICE_OUT_ANLG_DOCK_HEADSET;
            } else if (headset == BIT_USB_HEADSET_DGTL) {
                outDevice = AudioManager.DEVICE_OUT_DGTL_DOCK_HEADSET;
            } else if (headset == BIT_HDMI_AUDIO) {
                outDevice = AudioManager.DEVICE_OUT_HDMI;
            } else {
                Slog.e(TAG, "setDeviceState() invalid headset type: " + headset);
                return;
            }

            if (LOG) {
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                Slog.v(TAG, "headset: " + headsetNameAddr +
                       (state == 1 ? " connected" : " disconnected"));
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            }

// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            String[] hs = headsetNameAddr.split("/");
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            if (outDevice != 0) {
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                if (LOG) {
                    Slog.v(TAG, "Output device address " + (hs.length > 1 ? hs[1] : "")
                           + " name " + hs[0]);
                }
                mAudioManager.setWiredDeviceConnectionState(outDevice, state,
                                                             (hs.length > 1 ? hs[1] : ""), hs[0]);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            }
            if (inDevice != 0) {

// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
              mAudioManager.setWiredDeviceConnectionState(inDevice, state,
                                                           (hs.length > 1 ? hs[1] : ""), hs[0]);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            }
        }
    }

    private String switchCodeToString(int switchValues, int switchMask) {
        StringBuilder sb = new StringBuilder();
        if ((switchMask & SW_HEADPHONE_INSERT_BIT) != 0 &&
                (switchValues & SW_HEADPHONE_INSERT_BIT) != 0) {
            sb.append("SW_HEADPHONE_INSERT ");
        }
        if ((switchMask & SW_MICROPHONE_INSERT_BIT) != 0 &&
                (switchValues & SW_MICROPHONE_INSERT_BIT) != 0) {
            sb.append("SW_MICROPHONE_INSERT");
        }
        return sb.toString();
    }

    class WiredAccessoryObserver extends UEventObserver {
        private final List<UEventInfo> mUEventInfo;
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
        private List<String> mDevPath = new ArrayList<String>();
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.

        public WiredAccessoryObserver() {
            mUEventInfo = makeObservedUEventList();
        }

        void init() {
            synchronized (mLock) {
                if (LOG) Slog.v(TAG, "init()");
                char[] buffer = new char[1024];
                for (int i = 0; i < mUEventInfo.size(); ++i) {
                    UEventInfo uei = mUEventInfo.get(i);
                    try {
                        int curState;
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                        String switchStatePath = uei.getSwitchStatePath();
                        FileReader file = new FileReader(switchStatePath);
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                        int len = file.read(buffer, 0, 1024);
                        file.close();
                        curState = Integer.parseInt((new String(buffer, 0, len)).trim());

                        if (curState > 0) {
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                            int index = switchStatePath.lastIndexOf(".");
                            if(switchStatePath.substring(index + 1, index + 2).equals("1")) {
                                mDetectedIntf = INTF_HDMI;
                            }
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                            updateStateLocked(uei.getDevPath(), uei.getDevName(), curState);
                        }
                    } catch (FileNotFoundException e) {
                        Slog.w(TAG, uei.getSwitchStatePath() +
                                " not found while attempting to determine initial switch state");
                    } catch (Exception e) {
                        Slog.e(TAG, "Error while attempting to determine initial switch state for "
                                + uei.getDevName(), e);
                    }
                }
            }

            // At any given time accessories could be inserted
            // one on the board, one on the dock and one on HDMI:
            // observe three UEVENTs
            for (int i = 0; i < mUEventInfo.size(); ++i) {
                UEventInfo uei = mUEventInfo.get(i);
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                String devPath = uei.getDevPath();

                if (mDevPath.contains(devPath))
                    continue;

// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                startObserving("DEVPATH=" + uei.getDevPath());
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                mDevPath.add(devPath);
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
            }
        }

        private List<UEventInfo> makeObservedUEventList() {
            List<UEventInfo> retVal = new ArrayList<UEventInfo>();
            UEventInfo uei;

            // Monitor h2w
            if (!mUseDevInputEventForAudioJack) {
                uei = new UEventInfo(NAME_H2W, BIT_HEADSET, BIT_HEADSET_NO_MIC, BIT_LINEOUT);
                if (uei.checkSwitchExists()) {
                    retVal.add(uei);
                } else {
                    Slog.w(TAG, "This kernel does not have wired headset support");
                }
            }

            // Monitor USB
            uei = new UEventInfo(NAME_USB_AUDIO, BIT_USB_HEADSET_ANLG, BIT_USB_HEADSET_DGTL, 0);
            if (uei.checkSwitchExists()) {
                retVal.add(uei);
            } else {
                Slog.w(TAG, "This kernel does not have usb audio support");
            }

            // Monitor HDMI
            //
            // If the kernel has support for the "hdmi_audio" switch, use that.  It will be
            // signalled only when the HDMI driver has a video mode configured, and the downstream
            // sink indicates support for audio in its EDID.
            //
            // If the kernel does not have an "hdmi_audio" switch, just fall back on the older
            // "hdmi" switch instead.
            uei = new UEventInfo(NAME_HDMI_AUDIO, BIT_HDMI_AUDIO, 0, 0);
            if (uei.checkSwitchExists()) {
                retVal.add(uei);
            } else {
                uei = new UEventInfo(NAME_HDMI, BIT_HDMI_AUDIO, 0, 0);
                if (uei.checkSwitchExists()) {
                    retVal.add(uei);
                } else {
                    Slog.w(TAG, "This kernel does not have HDMI audio support");
                }
            }

// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            for (String conn : DP_AUDIO_CONNS) {
                // Monitor DisplayPort
                uei = new UEventInfo(conn, BIT_HDMI_AUDIO, 0, 0);
                if (uei.checkSwitchExists()) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                    Slog.i(TAG, "Adding " + conn + " with " + uei.toString() + " to monitor list");
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                    retVal.add(uei);
                }
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            }
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            return retVal;
        }

        @Override
        public void onUEvent(UEventObserver.UEvent event) {
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            String devPath = event.get("DEVPATH");
            String name = event.get("NAME");
            int state = 0;
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
            if (LOG) {
                Slog.v(TAG, "onUEvent event=" + event.toString());
            }
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            if (name == null)
                name = event.get("SWITCH_NAME");
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio

            try {
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                if (name.startsWith(NAME_DP_AUDIO)) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                    String state_str = event.get("STATE");
                    int offset = 0;
                    int length = state_str.length();

                    //parse DP=1\nHDMI=1\0
                    while (offset < length) {
                        int equals = state_str.indexOf('=', offset);

                        if (equals > offset) {
                            String intf_name = state_str.substring(offset,
                                                                   equals);

// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                            if (intf_name.equals("DP") || intf_name.equals("HDMI")) {
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                                state = Integer.parseInt(
                                            state_str.substring(equals + 1,
                                                                equals + 2));
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                                if (state == 1) {
                                    mDetectedIntf = intf_name;
                                    break;
                                }
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                            }
                        }

                        offset = equals + 3;
                     }
                } else {
                    state = Integer.parseInt(event.get("SWITCH_STATE"));
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                }
            } catch (NumberFormatException e) {
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                 Slog.i(TAG, "couldn't get state from event, checking node");

                for (int i = 0; i < mUEventInfo.size(); ++i) {
                    UEventInfo uei = mUEventInfo.get(i);

                    if (name.equals(uei.getDevName())) {
                        char[] buffer = new char[1024];
                        int len = 0;

                        try {
                            FileReader file = new FileReader(
                                                      uei.getSwitchStatePath());
                            len = file.read(buffer, 0, 1024);
                            file.close();
                        } catch (FileNotFoundException e1) {
                            Slog.e(TAG, "file not found");
                            break;
                        } catch (Exception e11) {
                            Slog.e(TAG, "" , e11);
                        }

                        try {
                             state = Integer.parseInt(
                                         (new String(buffer, 0, len)).trim());
                        } catch (NumberFormatException e2) {
                            Slog.e(TAG, "could not convert to number");
                            break;
                        }
                        break;
                    }
                }
            }

            synchronized (mLock) {
                updateStateLocked(devPath, name, state);
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            }
        }

// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
        private int getStreamIndex(String devPath) {
            // find first valid stream index
            for (int i =0; i < MAX_DP_COUNT; i++) {
                if (!streamsInUse[i]) {
                    streamsInUse[i] = true;
                    Slog.v(TAG, "getStreamIndex for " + devPath + " got " + i);
                    streamIndexMap.put(devPath, i);
                    return i;
                }
            }
            return 0;
        }

        private void removeDevice(String devPath) {
            if (streamIndexMap.containsKey(devPath)) {
                int index = streamIndexMap.get(devPath);
                streamsInUse[index] = false;
                streamIndexMap.remove(devPath);
                Slog.v(TAG, "removeDevice for " + devPath + " for stream " + index);
            }
        }

// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
        private void updateStateLocked(String devPath, String name, int state) {
            for (int i = 0; i < mUEventInfo.size(); ++i) {
                UEventInfo uei = mUEventInfo.get(i);
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                Slog.v(TAG, "uei.getDevPath=" + uei.getDevPath() + " uei=" + uei.toString());
                Slog.v(TAG, "uevent.devPath=" + devPath + ";name=" + name + ";state=" + state);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections

                if (devPath.equals(uei.getDevPath())) {
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                    if (state == 1) {
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                        int stream = getStreamIndex(devPath);
                        Slog.v(TAG, "devPath" + devPath + ";stream=" + stream);
                        uei.setStreamIndex(stream);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                        int newControllerIdx = (mDetectedIntf.equals(INTF_DP)) ? 0 : 1;
                        uei.setCableIndex(newControllerIdx);
                    }
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                    updateLocked(name,
                                 uei.getDevAddress(),
                                 uei.computeNewHeadsetState(mHeadsetState, state),
                                 false /*isSynchronous*/);
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections

                    if (state == 0) {
                        removeDevice(devPath);
                    }
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                    return;
                }
            }
        }

        private final class UEventInfo {
            private final String mDevName;
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            private String mDevAddress;
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            private final int mState1Bits;
            private final int mState2Bits;
            private final int mStateNbits;
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            private int mDevIndex;
            private int mCableIndex;
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio

// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            public UEventInfo(String devName, int state1Bits,
                              int state2Bits, int stateNbits) {
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                mDevName = devName;
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                mDevAddress = "controller=0;stream=0";
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                mState1Bits = state1Bits;
                mState2Bits = state2Bits;
                mStateNbits = stateNbits;
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                mDevIndex = -1;
                mCableIndex = -1;

                if (mDevName.startsWith(NAME_DP_AUDIO)) {
                    int idx = mDevName.indexOf("/");
                    if (idx != -1) {
                        int idx2 = mDevName.indexOf("/", idx+1);
                        assert(idx2 != -1);
                        int dev = Integer.parseInt(mDevName.substring(idx+1, idx2));
                        int cable = Integer.parseInt(mDevName.substring(idx2+1));
                        checkDevIndex(dev);
                        checkCableIndex(cable);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                        if (LOG) {
                            Slog.v(TAG, "UEventInfo name" + mDevName + "mDevAddress=" + mDevAddress
                                        + "mDevIndex="+ mDevIndex + "mCableIndex="+mCableIndex);
                        }
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                    }
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                }
            }

// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            private void checkDevIndex(int dev_index) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                int index = 0;
                char[] buffer = new char[1024];
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                while (true) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                    String devPath = String.format(Locale.US,
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-04-26: Audio: WiredAccessoryManager: update extcon file paths
                          "/sys/devices/platform/soc/%s/extcon/extcon%d/name",
// QTI_END: 2018-04-26: Audio: WiredAccessoryManager: update extcon file paths
// QTI_BEGIN: 2021-07-20: Audio: WiredAccessoryManager: use dev index from UEventInfo to form devPath
                                                   NAME_DP_AUDIO, dev_index);
// QTI_END: 2021-07-20: Audio: WiredAccessoryManager: use dev index from UEventInfo to form devPath
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                    if (LOG) {
                        Slog.v(TAG, "checkDevIndex " + devPath);
                    }
                    File f = new File(devPath);
                    if (!f.exists()) {
                        Slog.e(TAG, "file " + devPath + " not found");
                        break;
                    }
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                    try {
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                        FileReader file = new FileReader(f);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                        int len = file.read(buffer, 0, 1024);
                        file.close();

                        String devName = (new String(buffer, 0, len)).trim();
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                        if (devName.startsWith(NAME_DP_AUDIO) && index == dev_index) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                            Slog.e(TAG, "set dev_index " + dev_index + " devPath " + devPath);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                            mDevIndex = dev_index;
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                            break;
                        } else {
                            index++;
                        }
                    } catch (Exception e) {
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                        Slog.e(TAG, "checkDevIndex exception " , e);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                        break;
                    }
                }
            }

// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            private void checkCableIndex(int cable_index) {
                if (mDevIndex == -1) {
                    return;
                }
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                int index = 0;
                char[] buffer = new char[1024];
                while (true)
                {
                    String cablePath = String.format(Locale.US,
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-04-26: Audio: WiredAccessoryManager: update extcon file paths
                        "/sys/devices/platform/soc/%s/extcon/extcon%d/cable.%d/name",
// QTI_END: 2018-04-26: Audio: WiredAccessoryManager: update extcon file paths
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                                                     NAME_DP_AUDIO, mDevIndex, index);
                    if (LOG) {
                        Slog.v(TAG, "checkCableIndex " + cablePath);
                    }
                    File f = new File(cablePath);
                    if (!f.exists()) {
                        Slog.e(TAG, "file " + cablePath + " not found");
                        break;
                    }
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                    try {
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                        FileReader file = new FileReader(f);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                        int len = file.read(buffer, 0, 1024);
                        file.close();

                        String cableName = (new String(buffer, 0, len)).trim();
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                        if (cableName.equals("HDMI") && index == cable_index) {
                            mCableIndex = index;
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                            Slog.i(TAG, "checkCableIndex set cable for HDMI " + cable_index +
                                        " cable " + cablePath);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                            break;
                        } else if (cableName.equals("DP") && index == cable_index) {
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                            mCableIndex = index;
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                            Slog.i(TAG, "checkCableIndex set cable for DP " + cable_index +
                                        " cable " + cablePath);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                            break;
                        } else {
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                            Slog.i(TAG, "checkCableIndex no name match, skip for cable " +
                                        cablePath);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                            index++;
                        }
                    } catch (Exception e) {
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                        Slog.e(TAG, "checkCableIndex exception", e);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                        break;
                    }
                }
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            }

// QTI_BEGIN: 2021-08-04: Audio: WiredAccessoryManager: Update stream index assignment
            public void setStreamIndex(int streamIndex) {
// QTI_END: 2021-08-04: Audio: WiredAccessoryManager: Update stream index assignment
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                String devAddress = mDevAddress;
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2021-08-04: Audio: WiredAccessoryManager: Update stream index assignment
                int index1 = mDevAddress.indexOf("=");
                int index2 = mDevAddress.indexOf("=", index1 + 1);

                String allExceptStreamIdx = mDevAddress.substring(0, index2 + 1);
                mDevAddress = allExceptStreamIdx + String.valueOf(streamIndex);
// QTI_END: 2021-08-04: Audio: WiredAccessoryManager: Update stream index assignment
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                Slog.i(TAG, "setStreamIndex streamIndex" + streamIndex + " devAddress " +
                            devAddress + " updated to " + mDevAddress);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2021-08-04: Audio: WiredAccessoryManager: Update stream index assignment
            }

// QTI_END: 2021-08-04: Audio: WiredAccessoryManager: Update stream index assignment
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
            public void setCableIndex(int cableIndex) {
                int index = mDevAddress.indexOf("=");
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                String devAddress = mDevAddress;
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
                String changeControllerIdx = mDevAddress.substring(0, index + 1) + cableIndex
                                              + mDevAddress.substring(index + 2);
                mDevAddress = changeControllerIdx;
// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
                Slog.i(TAG, "setCableIndex cableIndex" + cableIndex + " devAddress " +
                            devAddress + " updated to " + mDevAddress);
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
// QTI_BEGIN: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
            }

// QTI_END: 2023-06-16: Audio: WiredAccessoryManager: support for DP/HDMI display on soc:qcom,msm-ext-disp.
            public String getDevName() {
                return mDevName;
            }

// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            public String getDevAddress() { return mDevAddress; }

// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
            public String getDevPath() {
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                if (mDevName.startsWith(NAME_DP_AUDIO)) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                    return String.format(Locale.US,
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                                         "/devices/platform/soc/%s/extcon/extcon%d",
                                         NAME_DP_AUDIO,
                                         mDevIndex);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                } else {
                    return String.format(Locale.US,
                                     "/devices/virtual/switch/%s",
                                     mDevName);
                }
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            }

            public String getSwitchStatePath() {
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                if (mDevName.startsWith(NAME_DP_AUDIO)) {
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                    return String.format(Locale.US,
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
// QTI_BEGIN: 2018-04-26: Audio: WiredAccessoryManager: update extcon file paths
                           "/sys/devices/platform/soc/%s/extcon/extcon%d/cable.%d/state",
// QTI_END: 2018-04-26: Audio: WiredAccessoryManager: update extcon file paths
// QTI_BEGIN: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
                           NAME_DP_AUDIO, mDevIndex, mCableIndex);
// QTI_END: 2018-06-18: Audio: WiredAccessoryManager: Support for multiple extconn devices
// QTI_BEGIN: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
                } else {
                    return String.format(Locale.US,
                                    "/sys/class/switch/%s/state",
                                    mDevName);
                }
// QTI_END: 2018-03-22: Audio: WiredAccessoryManager: Add support for DisplayPort Audio
            }

            public boolean checkSwitchExists() {
                File f = new File(getSwitchStatePath());
                return f.exists();
            }

            public int computeNewHeadsetState(int headsetState, int switchState) {
                int preserveMask = ~(mState1Bits | mState2Bits | mStateNbits);
                int setBits = ((switchState == 1) ? mState1Bits :
                        ((switchState == 2) ? mState2Bits :
                                ((switchState == mStateNbits) ? mStateNbits : 0)));

                return ((headsetState & preserveMask) | setBits);
            }
// QTI_BEGIN: 2024-05-27: Audio: base: Fix stream value for Multi DP connections

            public String toString() {
                return "UEventInfo " +
                       " name=" + mDevName +
                       " mDevAddress=" + mDevAddress +
                       " mDevIndex=" + mDevIndex +
                       " mCableIndex=" + mCableIndex;
            }
// QTI_END: 2024-05-27: Audio: base: Fix stream value for Multi DP connections
        }
    }

    private class WiredAccessoryExtconObserver extends ExtconStateObserver<Pair<Integer, Integer>> {
        private final List<ExtconInfo> mExtconInfos;

        WiredAccessoryExtconObserver() {
            mExtconInfos = ExtconInfo.getExtconInfoForTypes(new String[] {
                    ExtconInfo.EXTCON_HEADPHONE,
                    ExtconInfo.EXTCON_MICROPHONE,
                    ExtconInfo.EXTCON_HDMI,
                    ExtconInfo.EXTCON_LINE_OUT,
            });
        }

        private void init() {
            for (ExtconInfo extconInfo : mExtconInfos) {
                Pair<Integer, Integer> state = null;
                try {
                    state = parseStateFromFile(extconInfo);
                } catch (FileNotFoundException e) {
                    Slog.w(TAG, extconInfo.getStatePath()
                            + " not found while attempting to determine initial state", e);
                } catch (IOException e) {
                    Slog.e(
                            TAG,
                            "Error reading " + extconInfo.getStatePath()
                                    + " while attempting to determine initial state",
                            e);
                }
                if (state != null) {
                    updateState(extconInfo, extconInfo.getName(), state);
                }
                if (LOG) Slog.d(TAG, "observing " + extconInfo.getName());
                startObserving(extconInfo);
            }

        }

// QTI_BEGIN: 2019-05-28: Audio: update WiredAccessoryManager to use ExtconUEventObserver based on events available
        public int uEventCount() {
            return mExtconInfos.size();
        }

// QTI_END: 2019-05-28: Audio: update WiredAccessoryManager to use ExtconUEventObserver based on events available
        @Override
        public Pair<Integer, Integer> parseState(ExtconInfo extconInfo, String status) {
            if (LOG) Slog.v(TAG, "status  " + status);
            int[] maskAndState = {0, 0};
            // extcon event state changes from kernel4.9
            // new state will be like STATE=MICROPHONE=1\nHEADPHONE=0
            if (extconInfo.hasCableType(ExtconInfo.EXTCON_HEADPHONE)) {
                updateBit(maskAndState, BIT_HEADSET_NO_MIC, status, ExtconInfo.EXTCON_HEADPHONE);
            }
            if (extconInfo.hasCableType(ExtconInfo.EXTCON_MICROPHONE)) {
                updateBit(maskAndState, BIT_HEADSET, status, ExtconInfo.EXTCON_MICROPHONE);
            }
            if (extconInfo.hasCableType(ExtconInfo.EXTCON_HDMI)) {
                updateBit(maskAndState, BIT_HDMI_AUDIO, status, ExtconInfo.EXTCON_HDMI);
            }
            if (extconInfo.hasCableType(ExtconInfo.EXTCON_LINE_OUT)) {
                updateBit(maskAndState, BIT_LINEOUT, status, ExtconInfo.EXTCON_LINE_OUT);
            }
            if (LOG) Slog.v(TAG, "mask " + maskAndState[0] + " state " + maskAndState[1]);
            return Pair.create(maskAndState[0], maskAndState[1]);
        }

        @Override
        public void updateState(ExtconInfo extconInfo, String name,
                Pair<Integer, Integer> maskAndState) {
            synchronized (mLock) {
                int mask = maskAndState.first;
                int state = maskAndState.second;
                updateLocked(
                        name,
                        "",
                        mHeadsetState & ~(mask & ~state) | (mask & state),
                        false /*isSynchronous*/);
                return;
            }
        }
    }

    /**
     * Updates the mask bit at {@code position} to 1 and the state bit at {@code position} to true
     * if {@code name=1}  or false if {}@code name=0} is contained in {@code state}.
     */
    private static void updateBit(int[] maskAndState, int position, String state, String name) {
        maskAndState[0] |= position;
        if (state.contains(name + "=1")) {
            maskAndState[0] |= position;
            maskAndState[1] |= position;
        } else if (state.contains(name + "=0")) {
            maskAndState[0] |= position;
            maskAndState[1] &= ~position;
        }
    }
}
