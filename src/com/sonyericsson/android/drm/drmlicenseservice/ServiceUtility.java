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

import android.content.Context;
import android.content.Intent;
import android.drm.DrmInfoEvent;
import android.drm.DrmErrorEvent;
import android.drm.DrmManagerClient;

/**
 * Utility class for communicating DRM license results to native agents and
 * broadcast receivers.
 */
public class ServiceUtility {

    /** Intent extra for result code */
    private static final String EXTRA_RESULT_CODE = "com.sonyericsson.drm.RESULT_CODE";

    /** Intent extra for file path */
    private static final String EXTRA_FILE_PATH = "com.sonyericsson.drm.EXTRA_FILE_PATH";

    /** Intent extra for MIME type */
    private static final String EXTRA_MIME_TYPE = "com.sonyericsson.drm.EXTRA_MIME_TYPE";

    /** Intent action for license download information */
    private static final String ACTION_LICENSE_DOWNLOAD_INFO =
            "com.sonyericsson.drm.LICENSE_DOWNLOAD_INFO";

    /**
     * Sends result message through callback and broadcast.
     *
     * @param context Application context
     * @param result Result code
     * @param filePath File path
     */
    public static void sendOnInfoResult(Context context, int result, String filePath) {
        if (context != null) {
            DrmManagerClient dmc = new DrmManagerClient(context);
            if (dmc != null) {
                sendOnInfoResult(context, dmc, result, filePath);
                dmc = null;
            }
        }
    }

    /**
     * Sends result message through callback and broadcast.
     *
     * @param context Application context
     * @param dmc Manager client
     * @param resultCode Result code
     * @param filePath File path, which can be null if there is no file path
     *            available
     */
    public static void sendOnInfoResult(Context context, DrmManagerClient dmc, int resultCode,
            String filePath) {
        if (dmc != null) {
            if (filePath != null && filePath.length() > 0) {
                /* Only send broadcast when file path is supplied */
                String path = filePath.replace("file://content.protected", "");
                sendInfoBroadcast(context, resultCode, filePath, dmc.getOriginalMimeType(path));
            }
        }
    }

    private static void sendInfoBroadcast(Context context, int resultCode, String filePath,
            String mimeType) {
        switch (resultCode) {
            case DrmInfoEvent.TYPE_RIGHTS_INSTALLED: // Fall-through
            case DrmErrorEvent.TYPE_RIGHTS_NOT_INSTALLED: // Fall-through
            case DrmErrorEvent.TYPE_RIGHTS_RENEWAL_NOT_ALLOWED: // Fall-through
            case DrmErrorEvent.TYPE_NOT_SUPPORTED: // Fall-through
            case DrmErrorEvent.TYPE_OUT_OF_MEMORY: // Fall-through
            case DrmErrorEvent.TYPE_NO_INTERNET_CONNECTION:
                final Intent intent = new Intent(ACTION_LICENSE_DOWNLOAD_INFO);
                intent.putExtra(EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(EXTRA_FILE_PATH, filePath);
                intent.putExtra(EXTRA_MIME_TYPE, mimeType);
                context.sendBroadcast(intent);
                break;
            default:
                /* No broadcast */
                break;
        }
    }
}
