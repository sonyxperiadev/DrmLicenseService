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

import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;
import com.sonyericsson.android.drm.drmlicenseservice.ServiceUtility;
import com.sonyericsson.android.drm.drmlicenseservice.IDrmLicenseServiceCallback;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.drm.DrmErrorEvent;
import android.drm.DrmInfoEvent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

public class DrmFeedbackJob extends StackableJob {
    public static final int TYPE_WEBINI_COUNT = 1;

    public static final int TYPE_FINISHED_JOB = 2;

    public static final int TYPE_FINISHED_WEBINI = 3;

    public static final int TYPE_CANCELLED = 4;

    public static final int TYPE_RENEW_RIGHTS = 5;

    public static final int TYPE_CONTENT_DOWNLOADED = 6;

    private int mType = 0;

    private String mGroupType = null;

    private String mFilePath = null;

    public DrmFeedbackJob(int type) {
        this(type, null, null);
    }

    public DrmFeedbackJob(int type, String groupType) {
        this(type, groupType, null);
    }

    public DrmFeedbackJob(int type, Uri fileUri) {
        this(type, null, fileUri);
    }

    public DrmFeedbackJob(int type, String groupType, Uri fileUri) {
        mType = type;
        mGroupType = groupType;
        if (fileUri != null) {
            this.mFilePath = fileUri.toString();
        }
    }

    public int getFeedbackJobType() {
        return mType;
    }

    public String getFeedbackJobGroupType() {
        return mGroupType;
    }

    public String getFilePath() {
        return mFilePath;
    }

    @Override
    public boolean executeNormal() {
        // Log.d(Constants.LOGTAG, "Will send DrmFeedback type exec:" + mType);
        Bundle parameters = new Bundle();
        try {
            switch (mType) {
                case TYPE_WEBINI_COUNT: {
                    int groups = mJobManager.getNumberOfGroups();
                    parameters.putInt("GROUP_COUNT", groups);
                    if (mFilePath != null) {
                        parameters.putString("WEB_INITIATOR", mFilePath);
                    }
                    sendToCallbacks(mType, true, parameters);
                    break;
                }
                case TYPE_FINISHED_JOB: {
                    // This job group has succeeded.
                    copyGenericParameters(parameters);
                    int groups = mJobManager.getNumberOfGroups();
                    parameters.putInt("GROUP_COUNT", groups);
                    parameters.putInt("GROUP_NUMBER", getGroupId());
                    if (mGroupType != null && mGroupType.length() > 0) {
                        parameters.putString("TYPE", mGroupType);
                    }
                    if (mFilePath != null) {
                        parameters.putString("CONTENT_URL", mFilePath);
                    }
                    sendToCallbacks(mType, true, parameters);
                    break;
                }
                case TYPE_FINISHED_WEBINI:
                    sendToCallbacks(mType, mJobManager.isAllJobsOk(), parameters);
                    break;
                case TYPE_RENEW_RIGHTS:
                    // Send message that rights has been received
                    copyGenericParameters(parameters);
                    if (mJobManager.getCallbackHandler() != null) {
                        if (mFilePath != null) {
                            parameters.putString("FILEPATH", mFilePath);
                        }
                        sendToCallbacks(mType, true, parameters);
                    } else {
                        sendOnInfoResult(DrmInfoEvent.TYPE_RIGHTS_INSTALLED);
                    }
                    break;
            }
            return true;
        } catch (RemoteException e) {
            // Log.w(Constants.LOGTAG, "Got exception when sending callback.");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void executeAfterEarlierFailure() {
        // Log.d(Constants.LOGTAG, "Will send DrmFeedback type cancel:" +
        // mType);
        Bundle parameters = new Bundle();
        try {
            switch (mType) {
                case TYPE_WEBINI_COUNT: {
                    copyGenericParameters(parameters);
                    if (mFilePath != null) {
                        parameters.putString("WEB_INITIATOR", mFilePath);
                    }
                    sendToCallbacks(mType, false, parameters);
                    break;
                }
                case TYPE_FINISHED_JOB: {
                    // This job group has failed.
                    copyGenericParameters(parameters);
                    int groups = mJobManager.getNumberOfGroups();
                    parameters.putInt("GROUP_COUNT", groups);
                    parameters.putInt("GROUP_NUMBER", getGroupId());
                    if (mGroupType != null && mGroupType.length() > 0) {
                        parameters.putString("TYPE", mGroupType);
                    }
                    sendToCallbacks(mType, false, parameters);
                    break;
                }
                case TYPE_FINISHED_WEBINI:
                    // Maybe this can be used to send info that jobmanager has
                    // been canceled by app...
                    sendToCallbacks(mType, false, parameters);
                    break;
                case TYPE_CANCELLED:
                    sendToCallbacks(mType, true, parameters);
                    break;
                case TYPE_RENEW_RIGHTS:
                    // Send message that rights renewal was unsuccessful
                    copyGenericParameters(parameters);
                    if (mJobManager.getCallbackHandler() != null) {
                        if (mFilePath != null) {
                            parameters.putString("FILEPATH", mFilePath);
                        }
                        sendToCallbacks(mType, false, parameters);
                    } else {
                        sendOnInfoResult(DrmErrorEvent.TYPE_RIGHTS_NOT_INSTALLED);
                    }
                    break;
            }
        } catch (RemoteException e) {
            // Log.w(Constants.LOGTAG,
            // "Got excepection while sending cancel callback.");
            e.printStackTrace();
        }
    }

    private void copyGenericParameters(Bundle parameters) {
        Bundle inParams = mJobManager.getParameters();
        if (inParams != null) {
            String customData = inParams.getString("CUSTOM_DATA_USED");
            if (customData != null) {
                parameters.putString("CUSTOM_DATA", customData);
                inParams.remove("CUSTOM_DATA_USED");
            }
            String redirectUrl = inParams.getString("REDIRECT_URL");
            if (redirectUrl != null && redirectUrl.length() > 0) {
                parameters.putString("REDIRECT_URL", redirectUrl);
                inParams.remove("REDIRECT_URL");
            }
            int httpError = inParams.getInt("HTTP_ERROR");
            if (httpError != 0) {
                parameters.putInt("HTTP_ERROR", httpError);
                inParams.remove("HTTP_ERROR");
            }
            int innerHttpError = inParams.getInt("INNER_HTTP_ERROR");
            if (innerHttpError != 0) {
                parameters.putInt("INNER_HTTP_ERROR", innerHttpError);
                inParams.remove("INNER_HTTP_ERROR");
            }
        }
    }

    private void sendOnInfoResult(int resultCode) {
        Context context = mJobManager.getContext();
        ServiceUtility.sendOnInfoResult(context, resultCode, mFilePath);
    }

    private void sendToCallbacks(int state, boolean status, Bundle parameters)
            throws RemoteException {
        IDrmLicenseServiceCallback cb = mJobManager.getCallbackHandler();
        if (cb != null) {
            if (!cb.onProgressReport(mJobManager.getSessionId(), state, status, parameters)) {
                throw new RemoteException();
            }
        }
    }

    @Override
    public boolean writeToDB(DrmJobDatabase msDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_NAME_TYPE, DatabaseConstants.JOBTYPE_DRM_FEEDBACK);
        values.put(DatabaseConstants.COLUMN_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL1, this.mFilePath);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL2, this.mType);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL3, this.mGroupType);
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
        this.mFilePath = c.getString(DatabaseConstants.COLUMN_DRMFEEDBACK_URI);
        this.mType = c.getInt(DatabaseConstants.COLUMN_DRMFEEDBACK_TYPE);
        this.mGroupType = c.getString(DatabaseConstants.COLUMN_DRMFEEDBACK_GROUP_TYPE);
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_POS_GRP_ID));
        return true;
    }
}
