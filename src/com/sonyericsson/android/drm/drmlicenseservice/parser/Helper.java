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

package com.sonyericsson.android.drm.drmlicenseservice.parser;

import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Helper {

    public static int Uint16FromBufferSmall(byte[] buffer, int offset) {
        int result = (buffer[1 + offset] & 0xFF) << 8 | (buffer[0 + offset] & 0xFF);
        return result;
    }

    public static int Uint24FromBuffer(byte[] buffer, int offset) {
        int result = (buffer[0 + offset] & 0xFF) << 16 | (buffer[1 + offset] & 0xFF) << 8
                | (buffer[2 + offset] & 0xFF);
        return result;
    }

    public static int Uint32FromBuffer(byte[] buffer, int offset) {
        int result = (buffer[0 + offset] & 0xFF) << 24 | (buffer[1 + offset] & 0xFF) << 16
                | (buffer[2 + offset] & 0xFF) << 8 | (buffer[3 + offset] & 0xFF);
        return result;
    }

    public static int Uint32FromBufferSmall(byte[] buffer, int offset) {
        int result = (buffer[3 + offset] & 0xFF) << 24 | (buffer[2 + offset] & 0xFF) << 16
                | (buffer[1 + offset] & 0xFF) << 8 | (buffer[0 + offset] & 0xFF);
        return result;
    }

    public static long Uint64FromBuffer(byte[] buffer, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(buffer, offset, 8);
        bb.order(ByteOrder.BIG_ENDIAN);
        long result = bb.getLong();
        return result;
    }

    public final static int BoxTypeForName(String name) {
        int res = -1;
        try {
            res = Uint32FromBuffer(name.getBytes("UTF-8"), 0);
        } catch (UnsupportedEncodingException e) {
            DrmLog.logException(e);
        }
        return res;
    }

}
