// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
/* Copyright (c) 2015-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#define LOG_TAG "ActTriggerJNI"

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>

#include <dlfcn.h>
#include <limits.h>
#include <string.h>

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android
{

// ----------------------------------------------------------------------------
/*
 * Stuct containing handle to dynamically loaded lib as well as function
 * pointers to key interfaces.
 */
typedef struct dlLibHandler {
    void *dlhandle;
    void (*startActivity)(const char *, int *);
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
    void (*startApp)(const char *, int *);
// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
    void (*resumeActivity)(const char *);
    void (*pauseActivity)(const char *);
    void (*stopActivity)(const char *);
    void (*init)(void);
    void (*deinit)(void);
    void (*miscActivity)(int, const char *, int, int, float *);
    const char *dlname;
}dlLibHandler;

/*
 * Init for activity trigger library
 */
static dlLibHandler mDlLibHandler = {
    NULL, NULL, NULL, NULL, NULL, NULL,
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
    NULL, NULL, NULL, "libqti-at.so"
// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
};

// ----------------------------------------------------------------------------

static void
com_android_internal_app_ActivityTrigger_native_at_init()
{
    bool errored = false;

// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-04-15: Core: perf: Use get API for perf Properties.
    mDlLibHandler.dlhandle = dlopen(mDlLibHandler.dlname, RTLD_NOW | RTLD_LOCAL);
// QTI_END: 2019-04-15: Core: perf: Use get API for perf Properties.
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
    if (mDlLibHandler.dlhandle == NULL) {
        return;
    }

    *(void **) (&mDlLibHandler.startActivity) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_start");
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
    if (mDlLibHandler.startActivity == NULL) {
// QTI_END: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
        errored = true;
    }
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."

    *(void **) (&mDlLibHandler.startApp) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_qspm_start");

// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
    if (!errored) {
        *(void **) (&mDlLibHandler.resumeActivity) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_resume");
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
        if (mDlLibHandler.resumeActivity == NULL) {
// QTI_END: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
            errored = true;
        }
    }
    if (!errored) {
        *(void **) (&mDlLibHandler.pauseActivity) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_pause");
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
        if (mDlLibHandler.pauseActivity == NULL) {
// QTI_END: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
            errored = true;
        }
    }
    if (!errored) {
        *(void **) (&mDlLibHandler.stopActivity) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_stop");
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
        if (mDlLibHandler.stopActivity == NULL) {
// QTI_END: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
            errored = true;
        }
    }
    if (!errored) {
        *(void **) (&mDlLibHandler.init) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_init");
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
        if (mDlLibHandler.init == NULL) {
// QTI_END: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
            errored = true;
        }
    }
    if (!errored) {
        *(void **) (&mDlLibHandler.miscActivity) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_misc");
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
        if (mDlLibHandler.miscActivity == NULL) {
// QTI_END: 2018-03-27: Core: Activity Trigger: Check return value of dlsym
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
            errored = true;
        }
    }
    if (errored) {
        mDlLibHandler.startActivity  = NULL;
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
        mDlLibHandler.startApp = NULL;
// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
        mDlLibHandler.resumeActivity = NULL;
        mDlLibHandler.pauseActivity  = NULL;
        mDlLibHandler.stopActivity = NULL;
        mDlLibHandler.miscActivity = NULL;
        if (mDlLibHandler.dlhandle) {
            dlclose(mDlLibHandler.dlhandle);
            mDlLibHandler.dlhandle = NULL;
        }
    } else {
        (*mDlLibHandler.init)();
    }
}

static void
com_android_internal_app_ActivityTrigger_native_at_deinit(JNIEnv *env, jobject clazz)
{
    if (mDlLibHandler.dlhandle) {
        mDlLibHandler.startActivity  = NULL;
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
        mDlLibHandler.startApp = NULL;
// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
        mDlLibHandler.resumeActivity = NULL;
        mDlLibHandler.pauseActivity  = NULL;
        mDlLibHandler.stopActivity = NULL;
        mDlLibHandler.miscActivity = NULL;

        *(void **) (&mDlLibHandler.deinit) = dlsym(mDlLibHandler.dlhandle, "activity_trigger_deinit");
        if (mDlLibHandler.deinit) {
            (*mDlLibHandler.deinit)();
        }

        dlclose(mDlLibHandler.dlhandle);
        mDlLibHandler.dlhandle = NULL;
    }
}

static jint
com_android_internal_app_ActivityTrigger_native_at_startActivity(JNIEnv *env, jobject clazz, jstring activity, jint flags)
{
    int activiyFlags = flags;
    if(mDlLibHandler.startActivity && activity) {
       const char *actStr = env->GetStringUTFChars(activity, NULL);
       if (actStr) {
           (*mDlLibHandler.startActivity)(actStr, &activiyFlags);
           env->ReleaseStringUTFChars(activity, actStr);
       }
    }
    return activiyFlags;
}

// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
static jint
com_android_internal_app_ActivityTrigger_native_at_startApp(JNIEnv *env, jobject clazz, jstring activity, jint flags)
{
    int activiyFlags = flags;
    if(mDlLibHandler.startApp && activity) {
       const char *actStr = env->GetStringUTFChars(activity, NULL);
       if (actStr) {
           (*mDlLibHandler.startApp)(actStr, &activiyFlags);
           env->ReleaseStringUTFChars(activity, actStr);
       }
    }
    return activiyFlags;
}

// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
static void
com_android_internal_app_ActivityTrigger_native_at_resumeActivity(JNIEnv *env, jobject clazz, jstring activity)
{
    if(mDlLibHandler.resumeActivity && activity) {
       const char *actStr = env->GetStringUTFChars(activity, NULL);
       if (actStr) {
           (*mDlLibHandler.resumeActivity)(actStr);
           env->ReleaseStringUTFChars(activity, actStr);
       }
    }
}

static void
com_android_internal_app_ActivityTrigger_native_at_pauseActivity(JNIEnv *env, jobject clazz, jstring activity)
{
    if(mDlLibHandler.pauseActivity && activity) {
       const char *actStr = env->GetStringUTFChars(activity, NULL);
       if (NULL != actStr) {
           (*mDlLibHandler.pauseActivity)(actStr);
           env->ReleaseStringUTFChars(activity, actStr);
       }
    }
}

static void
com_android_internal_app_ActivityTrigger_native_at_stopActivity(JNIEnv *env, jobject clazz, jstring activity)
{
    if(mDlLibHandler.stopActivity && activity) {
       const char *actStr = env->GetStringUTFChars(activity, NULL);
       if (NULL != actStr) {
           (*mDlLibHandler.stopActivity)(actStr);
           env->ReleaseStringUTFChars(activity, actStr);
       }
    }
}

static jfloat
com_android_internal_app_ActivityTrigger_native_at_miscActivity(JNIEnv *env, jobject clazz, jint func, jstring activity, jint type, jint flag)
{
    float scaleValue = -1.0f;
    if (mDlLibHandler.miscActivity && activity && func) {
        const char *actStr = env->GetStringUTFChars(activity, NULL);
        if (actStr) {
            (*mDlLibHandler.miscActivity)(func, actStr, type, flag, &scaleValue);
            env->ReleaseStringUTFChars(activity, actStr);
        }
    }
    return scaleValue;
}

// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"native_at_startActivity",  "(Ljava/lang/String;I)I", (void *)com_android_internal_app_ActivityTrigger_native_at_startActivity},
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
// QTI_BEGIN: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
    {"native_at_startApp", "(Ljava/lang/String;I)I", (void *)com_android_internal_app_ActivityTrigger_native_at_startApp},
// QTI_END: 2019-09-24: Core: Revert "Add a filter to recieve app start info."
// QTI_BEGIN: 2018-02-20: Core: Performance: Activity Trigger frameworks support
    {"native_at_resumeActivity", "(Ljava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_resumeActivity},
    {"native_at_pauseActivity", "(Ljava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_pauseActivity},
    {"native_at_stopActivity", "(Ljava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_stopActivity},
    {"native_at_deinit",         "()V",                   (void *)com_android_internal_app_ActivityTrigger_native_at_deinit},
    {"native_at_miscActivity", "(ILjava/lang/String;II)F", (void *)com_android_internal_app_ActivityTrigger_native_at_miscActivity},
};

int register_com_android_internal_app_ActivityTrigger(JNIEnv *env)
{
    com_android_internal_app_ActivityTrigger_native_at_init();

    return AndroidRuntime::registerNativeMethods(env,
            "com/android/internal/app/ActivityTrigger", gMethods, NELEM(gMethods));
}

}   // namespace android
// QTI_END: 2018-02-20: Core: Performance: Activity Trigger frameworks support
