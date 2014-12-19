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

import android.util.SparseArray;

public class Box {
    public static final Box EmptyBox = new Box();

    public long offset;
    public long size;
    public BoxType boxType;
    public SparseArray<Box> subBoxes = new SparseArray<Box>();

    public Box() {
        boxType = new BoxType(0);
        offset = 0;
        size = 0;
    }

    public Box(long offset, long size, BoxType type) {
        this.offset = offset;
        this.size = size;
        this.boxType = type;
    }

    public long end() {
        return size > 0 ? offset + size : -1;
    }
}
