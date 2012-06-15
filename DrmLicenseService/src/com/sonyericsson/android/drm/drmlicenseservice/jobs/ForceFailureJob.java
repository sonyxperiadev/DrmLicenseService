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
 * Sony Mobile Communications AB.
 * Portions created by Sony Mobile Communications AB are Copyright (C) 2012
 * Sony Mobile Communications AB. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;

import android.content.ContentValues;
import android.database.Cursor;

public class ForceFailureJob extends StackableJob {

    @Override
    public boolean executeNormal() {
        return false;
    }

    @Override
    public boolean writeToDB(DrmJobDatabase jobDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_TYPE,
                DatabaseConstants.JOBTYPE_FORCE_FAILURE);
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GRP_ID, this.getGroupId());
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
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_TASKS_POS_GRP_ID));
        return true;
    }
}
