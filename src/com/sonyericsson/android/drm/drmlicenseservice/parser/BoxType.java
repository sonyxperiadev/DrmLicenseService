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

import com.sonyericsson.android.drm.drmlicenseservice.DrmLog;

import java.io.UnsupportedEncodingException;

public class BoxType {
    int type = 0;

    Uuid uuid = null;

    public BoxType(int type) {
        this.type = type;
    }

    public BoxType(byte[] p4CC, int offset) {
        type = Helper.Uint32FromBuffer(p4CC, offset);
    }

    public BoxType(String p4CC) {
        byte[] bytes = null;
        try {
            bytes = p4CC.getBytes("UTF-8");
            type = Helper.Uint32FromBuffer(bytes, 0);
        } catch (UnsupportedEncodingException e) {
            DrmLog.logException(e);
        }
    }

    public BoxType(Uuid uuid) {
        type = DrmPiffParser.BOX_TYPE_UUID;
        this.uuid = uuid;
    }

    @Override
    public boolean equals(Object object) {
        boolean res = false;
        if (object != null && object.getClass() == getClass()) {
            BoxType boxType = (BoxType)object;
            res = type == boxType.type
                    && (uuid == null || boxType.uuid == null || uuid.equals(boxType.uuid));
        }
        return res;
    }

    @Override
    public int hashCode() {
        return type;
    }

}
