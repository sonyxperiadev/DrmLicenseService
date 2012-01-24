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
* The Initial Developer of the Original Code is
* Sony Ericsson Mobile Communications AB.
* Portions created by Sony Ericsson Mobile Communications AB are Copyright (C) 2011
* Sony Ericsson Mobile Communications AB. All Rights Reserved.
*
* Contributor(s):
*
* ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Broadcast receiver for DRM License Service actions, normally called from DRM-aware
 * applications for license renewal.
 */
public class DrmLicenseServiceBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();
        //Log.d(Constants.LOGTAG, "Broadcast receiver got intent " + intent);
        if (intentAction.equals(Constants.DRM_RENEW_RIGHTS_ACTION)) {
            // Start service to handle the renew flow
            if (Constants.DEBUG) {
                Log.d(Constants.LOGTAG, "Received intent to renew rights for: "
                        + intent.getDataString());
            }
            intent.setClass(context, DrmLicenseService.class);
            intent.setAction(Constants.INTENT_ACTION_DRM_SERVICE_RENEW);
            context.startService(intent);
        } else if (intentAction.equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (Constants.DEBUG) {
                Log.d(Constants.LOGTAG, "Boot completed, will wipe job database");
            }
            intent.setClass(context, DrmLicenseService.class);
            context.startService(intent);
        }
    }
}
