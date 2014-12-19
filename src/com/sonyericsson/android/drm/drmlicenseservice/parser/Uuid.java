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

import java.util.Arrays;

public class Uuid {
    public static final int UUID_LEN = 16;

    public byte[] value = new byte[UUID_LEN];

    public Uuid() {
    }

    public Uuid(byte[] value, int offset, int len) {
        this.value = Arrays.copyOfRange(value, offset, offset + len);
    }

    public Uuid(byte[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean equals(Object object) {
        boolean res = false;
        if (object != null && getClass() == object.getClass()) {
            Uuid uuid = (Uuid)object;
            res = Arrays.equals(value, uuid.value);
        }
        return res;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}
