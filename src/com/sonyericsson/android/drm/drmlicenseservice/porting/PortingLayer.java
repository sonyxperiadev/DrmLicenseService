/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is DRM License Service.
 *
 * The Initial Developer of the Original Code is Sony Mobile Communications Inc.
 * Portions created by Sony Mobile Communications Inc. are Copyright (C) 2014
 * Sony Mobile Communications Inc. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.porting;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

import java.util.List;

// TODO
// This is class for tts synchronous processing implementation.
// Each OEM manufacturer should implement/replace with their tts synchronization solutions if needed.
public class PortingLayer {

    // ACTION_UPDATE_CLOCK is intent action name for secure clock is synchronized to tts.
    private static final String ACTION_UPDATE_CLOCK =
            "com.sonymobile.secureclockservice.UPDATE_CLOCK";

    public static boolean synchronizeTts(Context context) {
        DrmLog.debug("start");

        boolean ret = sendSecureClockUpdate(context);
        if (!ret) {
            DrmLog.error("sendSecureClockUpdate error");
        }

        DrmLog.debug("end");

        return ret;
    }

    private static boolean sendSecureClockUpdate(Context context) {
        boolean ret = false;

        DrmLog.debug("start");
        List<ResolveInfo> resolveInfos = lookUpService(context);
        if (resolveInfos == null || resolveInfos.size() == 0) {
            DrmLog.error("SecureClockService not found");
        } else if(resolveInfos.size() > 1) {
            DrmLog.error("More than one receiver");
        } else {
            ResolveInfo serviceInfo = resolveInfos.get(0);
            Intent service = getExplicitIapIntent(serviceInfo);
            if (service != null) {
                service.setAction(ACTION_UPDATE_CLOCK);
                context.startService(service);
                ret = true;
            }
        }
        DrmLog.debug("end");

        return ret;
    }

    private static List<ResolveInfo> lookUpService(Context context) {
        DrmLog.debug("start");
        PackageManager pm = context.getPackageManager();
        Intent implicitIntent = new Intent(ACTION_UPDATE_CLOCK);
        List<ResolveInfo> resolveinfos = pm.queryIntentServices(implicitIntent, 0);
        DrmLog.debug("end");
        return resolveinfos;
    }

    private static Intent getExplicitIapIntent(ResolveInfo serviceInfo) {
        DrmLog.debug("start");
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        Intent iapIntent = new Intent();
        iapIntent.setComponent(component);
        DrmLog.debug("end");
        return iapIntent;
    }
}
