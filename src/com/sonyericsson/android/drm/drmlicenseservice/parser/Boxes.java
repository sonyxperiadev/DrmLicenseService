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

import java.util.List;
import java.util.ArrayList;

public class Boxes {

    public static class FileTypeBox extends Box {
        public int majorBrand;
        public int minorVersion;
        List<Integer> compatibleBrands = new ArrayList<Integer>();

        public FileTypeBox(long anOffset, long aSize, BoxType aBoxType) {
            super(anOffset, aSize, aBoxType);
        }
    };

    public static class ProtSysSpecificHeaderBox extends Box {
        public Uuid systemId;
        public int dataSize;
        public byte []data;

        public ProtSysSpecificHeaderBox(long anOffset, long aSize,BoxType aBoxType) {
            super(anOffset, aSize, aBoxType);
        }
    };
}
