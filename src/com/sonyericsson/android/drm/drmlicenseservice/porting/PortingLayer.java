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

import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

// TODO
// This is class for tts synchronous processing implementation.
// Each OEM manufacturer should implement/replace with their tts synchronization solutions if needed.
public class PortingLayer {

    public static boolean synchronizeTts(Context context) {
        boolean ret = true;

        DrmLog.debug("start");

        DrmLog.debug("end");

        return ret;
    }

}
