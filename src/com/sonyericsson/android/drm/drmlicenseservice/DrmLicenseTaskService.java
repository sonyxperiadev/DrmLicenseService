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

package com.sonyericsson.android.drm.drmlicenseservice;

import com.sonyericsson.android.drm.drmlicenseservice.porting.PortingLayer;
import com.sonyericsson.android.drm.drmlicenseservice.RequestManager.RedirectCallback;
import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class DrmLicenseTaskService extends IntentService {

    private RedirectCallback mCallback = new RedirectCallback() {

        @Override
        public void onLaunchLuiUrl(String url) {
            DrmLog.debug("start");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getBaseContext().startActivity(i);
            DrmLog.debug("end");
        }
    };

    public DrmLicenseTaskService() {
        super("DrmLicenseTaskService");
        DrmLog.debug("start");
        setIntentRedelivery(true);
        DrmLog.debug("end");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        DrmLog.debug("start");
        Bundle extras = intent.getExtras();
        long sessionId = 0;
        int intentType = -1;

        // This is used for tts synchronization before acquiring license.
        // TTS synchronization process should be implemented by each oem manufacturer if needed.
        boolean ret = PortingLayer.synchronizeTts(this);
        if (ret) {
            if (extras != null) {
                sessionId = extras.getLong(Constants.DLS_INTENT_SESSION_ID, Constants.NOT_AIDL_SESSION);
                intentType = extras.getInt(Constants.DLS_INTENT_TYPE, -1);

                SessionManager.getInstance().makeSureAIDLSessionIsOpen(sessionId);
            }
            switch (intentType) {
                case Constants.DLS_INTENT_TYPE_FINISHED_WEBI:
                    if (sessionId > Constants.NOT_AIDL_SESSION) {
                        if (!SessionManager.getInstance().isCancelled(sessionId)) {
                            SessionManager.getInstance().callback(sessionId,
                                    Constants.PROGRESS_TYPE_FINISHED_WEBINI, true, new Bundle());
                        } else {
                            // Notify that session cancel is now complete.
                            SessionManager.getInstance().callback(sessionId,
                                    Constants.PROGRESS_TYPE_CANCELLED, true, new Bundle());
                            // it's now safe to remove it from cancelled list
                            SessionManager.getInstance().clearCancelled(sessionId);
                        }
                    }
                    break;
                case Constants.DLS_INTENT_TYPE_TASK:
                default:
                    RequestManager requestManager = new RequestManager(getBaseContext(), extras,
                            mCallback);
                    requestManager.execute();
            }
        } else {
            DrmLog.error("synchronizeTts error");
        }
        DrmLog.debug("Finished handle intent" + intentType);
        DrmLog.debug("end");
    }

}
