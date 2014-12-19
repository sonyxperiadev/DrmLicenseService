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
 * Portions created by Sony Mobile Communications Inc. are Copyright (C) 2014
 * Sony Mobile Communications Inc. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Receiver for intents from the browser. Browser intents must be
 * received in an Activity.
 */
public class DrmLicenseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        intent.setClass(this, DrmLicenseService.class);
        intent.setAction(DrmLicenseActivity.class.getName());
        startService(intent);
        if (getIntent() != null && getIntent().getType() != null &&
                getIntent().getType().length() > 0) {
            // Only close activity when launched with an intent containing an
            // type. Type will be provided when downloading a webinitiator.
            // Type will NOT be provided when running JUnit tests.
            finish();
        }
    }
}
