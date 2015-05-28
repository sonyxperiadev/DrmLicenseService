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

public class ByteArrayBuffer {

    private byte[] mDest;

    private int mPosition;

    public ByteArrayBuffer(int capacity) {
        mDest = new byte[capacity];
        mPosition = 0;
    }

    public void append(byte[] buffer, int offset, int length) {
        if (mDest.length < mPosition + length) {
            extend();
            append(buffer, offset, length);
        } else {
            System.arraycopy(buffer, offset, mDest, mPosition, length);
            mPosition += length;
        }
    }

    private void extend() {
        byte[] newDest = new byte[(mDest.length * 2)];
        System.arraycopy(mDest, 0, newDest, 0, mPosition);
        mDest = newDest;
    }

    public byte[] toByteArray() {
        byte[] res = new byte[mPosition];
        System.arraycopy(mDest, 0, res, 0, mPosition);
        return res;
    }

    public boolean isEmpty() {
        return mPosition == 0;
    }
}
