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
 * Portions created by Sony Mobile Communications Inc. are Copyright (C) 2015
 * Sony Mobile Communications Inc. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.utils;

import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;

import java.io.IOException;
import java.io.InputStream;

public class Utils {

    public static byte[] inputStreamToByteArray(InputStream is) {
        byte[] result = null;
        try {
            ByteArrayBuffer data = null;
            int read = 0;
            byte[] buffer = new byte[1024];
            data = new ByteArrayBuffer(10000);
            while ((read = is.read(buffer, 0, buffer.length)) != -1) {
                data.append(buffer, 0, read);
            }
            if (!data.isEmpty()) {
                result = data.toByteArray();
            }
        } catch (IOException e) {
            DrmLog.logException(e);
        }
        return result;
    }

    /*
     * Helper function to convert byte array to inputstream reader.
     * Checks for utf-8 or utf-16 ByteOrderMarks
     */
    public static InputStreamReader getInputStreamReader(byte[] bytes) {
        InputStreamReader res = null;
        String enc = "UTF-8"; // deault character encoding
        InputStream is = new ByteArrayInputStream(bytes);
        if (is != null) {
            try {
                int rc = 0, chk = 0;
                is.mark(0);
                while (rc < 4) {
                    int i = is.read();
                    if (i == -1) {
                        break;
                    }
                    chk = (chk << 8) | i;
                    rc++;
                }
                if ((chk & 0x0ffff0000) == 0x0FEFF0000) {
                    enc = "UTF-16BE";
                }
                else if ((chk & 0x0ffff0000) == 0x0fffe0000) {
                    enc = "UTF-16LE";
                }
                is.reset();
                res = new InputStreamReader(is, enc);
            } catch (IOException e) {
                DrmLog.logException(e);
            }
        }
        return res;
    }
}
