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

public class ProcessMeteringDataJob extends StackableJob {
    private String mCertificateServer = null;
    private String mMeteringId = null;
    private String mCustomData = "";
    private String mMaxPackets = "0";
    private boolean mAllowedToFetchCert = false;
    private int mAllowedRemainingPackets = 0;
    private String mMeteringStatus = "NoCertStored";

    private DrmInfoRequest createRequestToGenerateMeterDataChallenge(String mimeType) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                mimeType);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GENERATE_METER_DATA_CHALLENGE);
        request.put(Constants.DRM_METERING_METERING_ID, mMeteringId);
        addCustomData(request, mCustomData);
        return request;
    }

    private DrmInfoRequest createRequestToProcessMeterDataResponse(String mimeType, String data) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                mimeType);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_METER_DATA_RESPONSE);
        request.put(Constants.DRM_DATA, data);
        return request;
    }

    public ProcessMeteringDataJob(String certificateServer, String meteringId,
            String customData, String maxPackets, boolean allowedToFetchCert) {
        mCertificateServer = certificateServer;
        mMeteringId = meteringId;
        if (customData != null) {
            mCustomData = customData;
        }
        if (maxPackets != null) {
            mMaxPackets = maxPackets;
            mAllowedRemainingPackets = Integer.parseInt(mMaxPackets);
        }
        mAllowedToFetchCert = allowedToFetchCert;
    }

    // Overall logic
    // 1 generate Metering Challenge
    // 2 send Metering Data Report to server
    // 3 process Metering Data Report response
    // if 1 returns no certificate stored then initiate GetMeteringCertificateJob
    // if 1 returns no more reports then terminate the job
    // if 2 fails then handle error and return

    @Override
    public boolean executeNormal() {
        boolean status = false;

        if (mCertificateServer != null && mCertificateServer.length() > 0 &&
                mMeteringId != null && mMeteringId.length() > 0) {
            status = generateMeteringDataChallenge();
        }
        return status;
    }

    @Override
    protected boolean handleResponse200(String data) {
        boolean isOk = false;
        //Log.d(Constants.LOGTAG, "Metering report response received from server");
        // Send message to engine to process metering data response
        DrmInfo reply = sendInfoRequest(createRequestToProcessMeterDataResponse(
                Constants.DRM_DLS_PIFF_MIME, data));
        if (reply == null) {
            reply = sendInfoRequest(createRequestToProcessMeterDataResponse(
                    Constants.DRM_DLS_MIME, data));
        }
        if (reply != null) {
            String replyStatus = (String)reply.get(Constants.DRM_STATUS);
            //Log.d(Constants.LOGTAG, "status is " + replyStatus);
            if (replyStatus != null && replyStatus.length() > 0 && replyStatus.equals("ok")) {
                isOk = true;

                if (mMaxPackets.equals("0") || mAllowedRemainingPackets > 0) {
                    isOk = generateMeteringDataChallenge();
                }
            } else {
                mJobManager.addParameter("HTTP_ERROR", -6);
            }
        } else {
            mJobManager.addParameter("HTTP_ERROR", -6);
        }
        return isOk;
    }

    private boolean generateMeteringDataChallenge() {
        boolean isOk = false;
        String url = null;

        // Send message to engine to get the Metering Data Report challenge
        DrmInfo reply = sendInfoRequest(createRequestToGenerateMeterDataChallenge(
                Constants.DRM_DLS_PIFF_MIME));
        if (reply == null) {
            reply = sendInfoRequest(createRequestToGenerateMeterDataChallenge(
                    Constants.DRM_DLS_MIME));
        }
        if (reply != null) {
            String replyStatus = (String)reply.get(Constants.DRM_STATUS);
            if (replyStatus != null && replyStatus.length() > 0 && replyStatus.equals("ok")) {
                String data = (String)reply.get(Constants.DRM_DATA);
                url = (String)reply.get(Constants.DRM_METERING_URL);
                mMeteringStatus = (String)reply.get(Constants.DRM_METERING_STATUS);
                if (mMeteringStatus.equals("ReportGenerated")) {
                    if (data != null && data.length() > 0 && url != null && url.length() > 0) {
                        // Post license challenge to server
                        //Log.d(Constants.LOGTAG, "Metering report sent to server");
                        //Log.d(Constants.LOGTAG, "url = " + url);
                        //Log.d(Constants.LOGTAG, "data length = " + data.length());
                        isOk = postMessage(url, data);
                        if (mAllowedRemainingPackets > 0) {
                            mAllowedRemainingPackets--;
                        }
                    } else {
                        //Log.d(Constants.LOGTAG, "No relevant challenge or url");
                    }
                } else if (mMeteringStatus.equals("NoMoreReports")) {
                    // No more to report
                    isOk = true;
                } else if (mMeteringStatus.equals("NoCertStored")) {
                    //Log.d(Constants.LOGTAG, "No Certificate stored");
                    if (mAllowedToFetchCert) {
                        //Log.d(Constants.LOGTAG, "Fetch certificate");
                        mJobManager.pushJob(new ProcessMeteringDataJob(mCertificateServer,
                                mMeteringId, mCustomData, mMaxPackets, false));
                        mJobManager.pushJob(new GetMeteringCertificateJob(mCertificateServer,
                                mMeteringId, mCustomData));
                        isOk = true;
                    }
                } else {
                    mJobManager.addParameter("HTTP_ERROR", -6);
                }
            } else {
                mJobManager.addParameter("HTTP_ERROR", -6);
            }
        } else {
            mJobManager.addParameter("HTTP_ERROR", -6);
            //Log.d(Constants.LOGTAG, "reply is null");
        }
        return isOk;
    }

    @Override
    public boolean writeToDB(DrmJobDatabase msDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_NAME_TYPE,
                DatabaseConstants.JOBTYPE_PROCESS_METERING_DATA);
        values.put(DatabaseConstants.COLUMN_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL1, this.mCertificateServer);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL2, this.mMeteringId);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL3, this.mCustomData);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL4, this.mMaxPackets);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL5,
                String.valueOf(this.mAllowedToFetchCert));
        long result = msDb.insert(values);
        if (result  != -1) {
            super.setDatabaseId(result);
        } else {
            status = false;
        }
        return status;
    }

    @Override
    public boolean readFromDB(Cursor c) {

        boolean isFetchAllowed = false;
        String isFetchAllowedString = c.getString(
                DatabaseConstants.COLUMN_PROCESS_METERING_DATA_ALLOWED_TO_FETCH_CERT);
        if (isFetchAllowedString != null) {
            isFetchAllowed = Boolean.parseBoolean(isFetchAllowedString);
        }
        this.mAllowedToFetchCert = isFetchAllowed;
        this.mCertificateServer = c.getString(
                DatabaseConstants.COLUMN_PROCESS_METERING_DATA_CERTIFICATE_SERVER);
        this.mMeteringId = c.getString(DatabaseConstants.COLUMN_PROCESS_METERING_DATA_METERING_ID);
        this.mCustomData = c.getString(DatabaseConstants.COLUMN_PROCESS_METERING_DATA_CUSTOM_DATA);
        this.mMaxPackets = c.getString(DatabaseConstants.COLUMN_PROCESS_METERING_DATA_MAX_PACKETS);
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_POS_GRP_ID));
        return true;
    }
}
