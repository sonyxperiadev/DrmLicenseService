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
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.Constants;
import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;

import android.content.ContentValues;
import android.database.Cursor;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;

public class AcknowledgeLicenseJob extends StackableJob {
    public String mChallenge = null;

    public String mUrl = null;

    private DrmInfoRequest createRequestToProcessLicenseAckResponse(String mimeType, String data) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                mimeType);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_LIC_ACK_RESPONSE);
        request.put(Constants.DRM_DATA, data);
        return request;
    }

    public AcknowledgeLicenseJob(String challenge, String url) {
        mChallenge = challenge;
        mUrl = url;
    }

    @Override
    public boolean executeNormal() {
        boolean isOk = false;
        if (mChallenge != null && mChallenge.length() > 0 && mUrl != null && mUrl.length() > 0) {
            isOk = postMessage(mUrl, mChallenge);
        }
        return isOk;
    }

    @Override
    protected boolean handleResponse200(String data) {
        boolean isOk = false;
        // Send message to engine to call processAckLicense
        DrmInfo reply = sendInfoRequest(createRequestToProcessLicenseAckResponse(
                Constants.DRM_DLS_PIFF_MIME, data));
        if (reply == null) {
            reply = sendInfoRequest(createRequestToProcessLicenseAckResponse(
                    Constants.DRM_DLS_MIME, data));
        }
        if (reply != null) {
            String replyStatus = (String)reply.get(Constants.DRM_STATUS);
            // Log.d(Constants.LOGTAG, "status is " + replyStatus);
            if (replyStatus != null && replyStatus.length() > 0 && replyStatus.equals("ok")) {
                isOk = true;
            } else {
                mJobManager.addParameter("HTTP_ERROR", -6);
            }
        } else {
            mJobManager.addParameter("HTTP_ERROR", -6);
        }
        return isOk;
    }

    @Override
    public boolean writeToDB(DrmJobDatabase msDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_NAME_TYPE,
                DatabaseConstants.JOBTYPE_ACKNOWLEDGE_LICENSE);
        values.put(DatabaseConstants.COLUMN_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL1, this.mUrl);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL2, this.mChallenge);
        long result = msDb.insert(values);
        if (result != -1) {
            super.setDatabaseId(result);
        } else {
            status = false;
        }
        return status;
    }

    @Override
    public boolean readFromDB(Cursor c) {
        this.mChallenge = c.getString(DatabaseConstants.COLUMN_ACK_CHALLENGE);
        this.mUrl = c.getString(DatabaseConstants.COLUMN_ACK_URL);
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_POS_GRP_ID));
        return true;
    }

}
