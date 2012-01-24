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
import android.net.Uri;

public class JoinDomainJob extends StackableJob {
    private String mController = null;

    private String mServiceId = Constants.ALL_ZEROS_DRM_ID;

    private String mAccountId = Constants.ALL_ZEROS_DRM_ID;

    private String mRevision = "0";

    private String mCustomData = "";

    private DrmInfoRequest createRequestToGenerateJoinDomainChallenge(String mime,
            String friendlyName) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_REGISTRATION_INFO, mime);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GENERATE_JOIN_DOM_CHALLENGE);
        request.put(Constants.DRM_DOMAIN_SERVICE_ID, mServiceId);
        request.put(Constants.DRM_DOMAIN_ACCOUNT_ID, mAccountId);
        request.put(Constants.DRM_DOMAIN_REVISION, mRevision);
        request.put(Constants.DRM_DOMAIN_FRIENDLY_NAME, friendlyName);
        addCustomData(request, mCustomData);
        return request;
    }

    private DrmInfoRequest createRequestToProcessJoinDomainResponse(String mime, String data) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_REGISTRATION_INFO, mime);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_JOIN_DOM_RESPONSE);
        request.put(Constants.DRM_DATA, data);
        return request;
    }

    public JoinDomainJob(String controller, String serviceId, String accountId, String revision,
            String customData) {
        mController = controller;
        if (serviceId != null) {
            mServiceId = serviceId;
        }
        if (accountId != null) {
            mAccountId = accountId;
        }
        if (revision != null) {
            mRevision = revision;
        }
        if (customData != null) {
            mCustomData = customData;
        }
    }

    @Override
    public boolean executeNormal() {
        boolean isOK = false;
        do {
            if (mController == null) {
                // Log.w(Constants.LOGTAG, "Missing domain controller URL");
                isOK = false;
                break;
            }
            if (mServiceId == Constants.ALL_ZEROS_DRM_ID
                    && mAccountId == Constants.ALL_ZEROS_DRM_ID) {
                // Log.w(Constants.LOGTAG,
                // "Missing both service ID and account ID");
                isOK = false;
                break;
            }

            try {
                String friendlyName = "";
                if (mJobManager.getParameters() != null
                        && mJobManager.getParameters().containsKey("FRIENDLY_NAME")) {
                    friendlyName = mJobManager.getParameters().getString("FRIENDLY_NAME");
                }

                // Send message to engine to get the join-domain challenge
                DrmInfo reply = sendInfoRequest(createRequestToGenerateJoinDomainChallenge(
                        Constants.DRM_DLS_PIFF_MIME, friendlyName));
                if (reply == null) {
                    reply = sendInfoRequest(createRequestToGenerateJoinDomainChallenge(
                            Constants.DRM_DLS_MIME, friendlyName));
                }

                // Process reply
                String replyStatus = (String)reply.get(Constants.DRM_STATUS);
                if (replyStatus.equals("ok")) {
                    String data = (String)reply.get(Constants.DRM_DATA);
                    isOK = postMessage(mController, data);
                } else {
                    mJobManager.addParameter("HTTP_ERROR", -6);
                }
            } catch (NullPointerException e) {
                if (mJobManager != null) {
                    mJobManager.addParameter("HTTP_ERROR", -6);
                }
                // Log.w(Constants.LOGTAG, "Unexpected null pointer exception");
            }
        } while (false);

        if (!isOK) {
            StackableJob removedJob = mJobManager.removeJob(DrmFeedbackJob.class.getName());
            DrmFeedbackJob dfj = (DrmFeedbackJob)removedJob;
            Uri uri = null;
            if (dfj != null) {
                if (dfj.getFilePath() != null) {
                    uri = Uri.parse(dfj.getFilePath());
                }
                mJobManager
                        .pushJob(new DrmFeedbackJob(dfj.getFeedbackJobType(), "JoinDomain", uri));
            }
        }

        return isOK;
    }

    @Override
    protected boolean handleResponse200(String data) {
        try {
            // Send message to engine to store the domain certificate
            DrmInfo reply = sendInfoRequest(createRequestToProcessJoinDomainResponse(
                    Constants.DRM_DLS_PIFF_MIME, data));
            if (reply == null) {
                reply = sendInfoRequest(createRequestToProcessJoinDomainResponse(
                        Constants.DRM_DLS_MIME, data));
            }

            // Process reply
            String replyStatus = (String)reply.get(Constants.DRM_STATUS);
            return replyStatus.equals("ok");
        } catch (NullPointerException e) {
            // Log.w(Constants.LOGTAG, "Unexpected null pointer exception");
        }
        return false;
    }

    @Override
    public boolean writeToDB(DrmJobDatabase msDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_NAME_TYPE, DatabaseConstants.JOBTYPE_JOIN_DOMAIN);
        values.put(DatabaseConstants.COLUMN_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL1, this.mController);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL2, this.mServiceId);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL3, this.mAccountId);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL4, this.mRevision);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL5, this.mCustomData);
        values.put(DatabaseConstants.COLUMN_NAME_GRP_ID, this.getGroupId());
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
        this.mController = c.getString(DatabaseConstants.COLUMN_JOIN_DOMAIN_CONTROLLER);
        this.mServiceId = c.getString(DatabaseConstants.COLUMN_JOIN_DOMAIN_SERVICE_ID);
        this.mAccountId = c.getString(DatabaseConstants.COLUMN_JOIN_DOMAIN_ACCOUNT_ID);
        this.mRevision = c.getString(DatabaseConstants.COLUMN_JOIN_DOMAIN_REVISION);
        this.mCustomData = c.getString(DatabaseConstants.COLUMN_JOIN_DOMAIN_CUSTOM_DATA);
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_POS_GRP_ID));
        return true;
    }
}
