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

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DrmLog {

    public static void debug(String message)
    {
        if (Constants.DEBUG) {
            int i = 3;
            String fullClassName = Thread.currentThread().getStackTrace()[i].getClassName();
            String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
            String methodName = Thread.currentThread().getStackTrace()[i].getMethodName();
            int line = Thread.currentThread().getStackTrace()[i].getLineNumber();
            Log.d(Constants.LOGTAG, className + "." + methodName + ":" + line + " " + message);
        }
    }

    public static void logException(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        DrmLog.debug("Exception " + e);
        DrmLog.debug(sw.toString());
    }
}
