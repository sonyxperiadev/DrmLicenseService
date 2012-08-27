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
 * Sony Ericsson Mobile Communications AB.
 * Portions created by Sony Mobile Communications AB are Copyright (C) 2012
 * Sony Mobile Communications AB. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;
import com.sonyericsson.android.drm.drmlicenseservice.Constants;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class LaunchLuiUrlIfFailureJob extends StackableJob {
    private String mLuiUrl = null;

    public LaunchLuiUrlIfFailureJob(String luiUrl) {
        this.mLuiUrl = luiUrl;
    }

    @Override
    public boolean executeNormal() {
        // Do nothing
        return true;
    }

    @Override
    public void executeAfterEarlierFailure() {
        if (mJobManager.getCallbackHandler() == null) {
            // Not called via AIDL, open redirectUrl in browser
            if (mLuiUrl != null && mLuiUrl.trim().length() > 8) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mLuiUrl));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mJobManager.getContext().startActivity(intent);
            }
        } else {
            Bundle parameters = mJobManager.getParameters();
            if (parameters != null && (parameters.containsKey(Constants.DRM_KEYPARAM_REDIRECT_URL)
                    || (parameters.containsKey(Constants.DRM_KEYPARAM_HTTP_ERROR) &&
                            parameters.getInt(Constants.DRM_KEYPARAM_HTTP_ERROR) == -5))) {
                // Do nothing, let DrmFeedbackJob handle it when we either have RedirectURL
                // already or if HTTP_ERROR == -5 (XML parsing failed)
            } else {
                mJobManager.addParameter(Constants.DRM_KEYPARAM_REDIRECT_URL, mLuiUrl);
            }
        }
    }

    @Override
    public boolean writeToDB(DrmJobDatabase jobDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_TYPE,
                DatabaseConstants.JOBTYPE_LAUNCH_LUIURL_IF_FAIL);
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GENERAL1, this.mLuiUrl);
        long result = jobDb.insert(values);
        if (result != -1) {
            super.setDatabaseId(result);
        } else {
            status = false;
        }
        return status;
    }

    @Override
    public boolean readFromDB(Cursor c) {
        this.mLuiUrl = c.getString(DatabaseConstants.COLUMN_LAUNCH_LUI_URL);
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_TASKS_POS_GRP_ID));
        return true;
    }
}
