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
import com.sonyericsson.android.drm.drmlicenseservice.ErrorMessageParser.ErrorData;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;
import android.net.Uri;

import java.io.File;

public class AcquireLicenseJob extends StackableJob {
    public String mHeader = null;
    public Uri mFileUri = null;
    public String mCustomData = null;

    public String mLA_URL = null;

    private boolean triedJoinDomain = false;
    private boolean triedRenewDomain = false;

    private DrmInfoRequest createRequestToGenerateLicenseChallenge(String mimeType) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                mimeType);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GENERATE_LIC_CHALLENGE);
        request.put(Constants.DRM_HEADER, mHeader);
        addCustomData(request, mCustomData);
        return request;
    }

    private DrmInfoRequest createRequestToProcessLicenseResponse(String mimeType, String data) {
        DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                mimeType);
        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_LIC_RESPONSE);
        request.put(Constants.DRM_DATA, data);
        return request;
    }

    public AcquireLicenseJob(String header) {
        this(header, null);
    }

    public AcquireLicenseJob(String header, String customData) {
        mHeader = header;
        mCustomData = customData;
    }

    public AcquireLicenseJob(Uri fileUri) {
        mFileUri = fileUri;
    }

    // Overall logic
    // 1 generate lic challenge
    // 2 send lic req to server
    // 3 proc lic resp
    // 4 init lic ack
    // if 2 fails then handle error and return

    @Override
    public boolean executeNormal() {
        boolean status = false;
        if (mHeader != null && mHeader.length() > 0) {
            // Apply XML character-entity encoding to previously unescaped '&' characters only
            mHeader = mHeader.replaceAll("&(?!amp;)", "&amp;");

            // Send message to engine to get the license challenge
            DrmInfo reply = sendInfoRequest(createRequestToGenerateLicenseChallenge(
                    Constants.DRM_DLS_PIFF_MIME));
            if (reply == null) {
                reply = sendInfoRequest(createRequestToGenerateLicenseChallenge(
                        Constants.DRM_DLS_MIME));
            }
            if (reply != null) {
                String replyStatus = (String)reply.get(Constants.DRM_STATUS);
                if (replyStatus != null && replyStatus.equals("ok")) {
                    String data = (String)reply.get(Constants.DRM_DATA);
                    mLA_URL = (String)reply.get(Constants.DRM_LA_URL);
                    if (mLA_URL != null && mLA_URL.length() > 0) {
                        // Post license challenge to server
                        status = postMessage(mLA_URL, data);
                    } else {
                        if (mJobManager != null) {
                            mJobManager.addParameter("HTTP_ERROR", -6);
                        }
                        //Log.d(Constants.LOGTAG, "mLA_URL is not valid");
                    }
                } else {
                    if (mJobManager != null) {
                        mJobManager.addParameter("HTTP_ERROR", -6);
                    }
                    //Log.d(Constants.LOGTAG, "replyStatus is not ok");
                }
            } else {
                if (mJobManager != null) {
                    mJobManager.addParameter("HTTP_ERROR", -6);
                }
                //Log.d(Constants.LOGTAG, "reply is null");
            }
        } else {
            String scheme;
            if (mFileUri != null
                    && ((scheme = mFileUri.getScheme()) == null || scheme.equals("file"))) {
                File file = new File(mFileUri.getEncodedPath());
                if (file != null && file.exists()) {
                    String mime = Constants.DRM_DLS_PIFF_MIME;
                    if (file.toString().endsWith(".pyv") || file.toString().endsWith(".pya")) {
                        mime = Constants.DRM_DLS_MIME;
                    }

                    DrmInfoRequest request = new DrmInfoRequest(
                            DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO, mime);
                    request.put(Constants.DRM_ACTION,
                            Constants.DRM_ACTION_GENERATE_LIC_CHALLENGE);
                    addCustomData(request, null);
                    //Log.d(Constants.LOGTAG, "Generate license challenge for "
                    //        + mFileUri.getEncodedPath());
                    request.put(Constants.DRM_DATA, mFileUri.getEncodedPath());
                    // Send message to engine to get the license challenge
                    DrmInfo reply = sendInfoRequest(request);
                    if (reply != null) {
                        String replyStatus = (String)reply.get(Constants.DRM_STATUS);
                        if (replyStatus != null && replyStatus.length() > 0
                                && replyStatus.equals("ok")) {
                            String data = (String)reply.get(Constants.DRM_DATA);
                            mLA_URL = (String)reply.get(Constants.DRM_LA_URL);
                            if (mLA_URL != null && mLA_URL.length() > 0) {
                                // Post license challenge to server
                                status = postMessage(mLA_URL, data);
                            } else {
                                if (mJobManager != null) {
                                    mJobManager.addParameter("HTTP_ERROR", -6);
                                }
                                //Log.d(Constants.LOGTAG, "mLA_URL is not valid");
                            }
                        } else if (mJobManager != null) {
                            mJobManager.addParameter("HTTP_ERROR", -6);
                        }
                    } else {
                        if (mJobManager != null) {
                            mJobManager.addParameter("HTTP_ERROR", -6);
                        }
                        //Log.d(Constants.LOGTAG, "Reply is null");
                    }
                } else {
                    if (mJobManager != null) {
                        mJobManager.addParameter("HTTP_ERROR", -5);
                    }
                    //Log.w(Constants.LOGTAG, "The file does not exist " + mFileUri);
                }
            } else {
                if (mJobManager != null) {
                    mJobManager.addParameter("HTTP_ERROR", -5);
                }
                //Log.w(Constants.LOGTAG, "Uri or Header is not valid ");
            }
        }
        return status;
    }

    @Override
    protected boolean handleResponse200(String data) {
        boolean isOk = false;
        // Send message to engine to call processLicense and
        // generateLicenseAcknowledge
        DrmInfo reply = sendInfoRequest(createRequestToProcessLicenseResponse(
                Constants.DRM_DLS_PIFF_MIME, data));
        if (reply == null) {
            reply = sendInfoRequest(createRequestToProcessLicenseResponse(
                    Constants.DRM_DLS_MIME, data));
        }
        if (reply != null) {
            String replyStatus = (String)reply.get(Constants.DRM_STATUS);
            //Log.d(Constants.LOGTAG, "status is " + replyStatus);
            if (replyStatus != null && replyStatus.length() > 0 && replyStatus.equals("ok")) {
                String ackLicChallenge = (String)reply.get(Constants.DRM_DATA);
                if (ackLicChallenge != null && ackLicChallenge.length() > 0) {
                    mJobManager.pushJob(new AcknowledgeLicenseJob(ackLicChallenge, mLA_URL));
                }
                isOk = true;
            } else if (mJobManager != null) {
                mJobManager.addParameter("HTTP_ERROR", -6);
            }
        } else if (mJobManager != null) {
            mJobManager.addParameter("HTTP_ERROR", -6);
        }
        return isOk;
    }

    /*
     * Handle JoinDomain error message DRM_E_SERVER_DOMAIN_REQUIRED, i.e., trigger join domain
     */
    public boolean handleError0x8004c605(ErrorData errorData) {
        if (triedJoinDomain) {
            mJobManager.addParameter("HTTP_ERROR", 500);
            mJobManager.addParameter("INNER_HTTP_ERROR", 0x8004c605);
            return false;
        }
        mJobManager.pushJob(this); // Rerun AcquireLicenseJob after join domain
        mJobManager.pushJob(new JoinDomainJob(errorData.getValue("RedirectUrl"),
                errorData.getValue("ServiceId"),
                errorData.getValue("AccountId"),
                errorData.getValue("Revision"),
                errorData.getValue("CustomData")));
        triedJoinDomain = true;
        return true;
    }

    /*
     * Handle JoinDomain error message DRM_E_SERVER_RENEW_DOMAIN, i.e., trigger join domain with the
     * new revision number (tagged as custom data)
     */
    public boolean handleError0x8004c606(ErrorData errorData) {
        if (triedRenewDomain) {
            mJobManager.addParameter("HTTP_ERROR", 500);
            mJobManager.addParameter("INNER_HTTP_ERROR", 0x8004c606);
            return false;
        }
        mJobManager.pushJob(this); // Rerun AcquireLicenseJob after domain renewal
        mJobManager.pushJob(new JoinDomainJob(errorData.getValue("RedirectUrl"),
                errorData.getValue("ServiceId"),
                errorData.getValue("AccountId"),
                errorData.getValue("CustomData"),
                null));
        triedRenewDomain = true;
        return true;
    }

    /*
     * Handle error message DRM_E_SERVER_SERVICE_SPECIFIC, i.e., launch browser if the message comes
     * with a redirect URL
     */
    public boolean handleError0x8004c604(ErrorData errorData) {
        String redirectUrl = errorData.getValue("RedirectUrl");
        if (mJobManager.getCallbackHandler() == null) {
            if (redirectUrl != null) {
                // Not called via AIDL, open redirectUrl in browser
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mJobManager.getContext().startActivity(intent);
            }
        } else {
            mJobManager.addParameter("HTTP_ERROR", 500);
            mJobManager.addParameter("INNER_HTTP_ERROR", 0x8004c604);
            if (redirectUrl != null) {
                mJobManager.addParameter("REDIRECT_URL", redirectUrl);
            }
        }
        return false;
    }

    @Override
    public boolean writeToDB(DrmJobDatabase msDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_NAME_TYPE, DatabaseConstants.JOBTYPE_ACQUIRE_LICENCE);
        values.put(DatabaseConstants.COLUMN_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        if (this.mFileUri != null ) {
            values.put(DatabaseConstants.COLUMN_NAME_GENERAL1, this.mFileUri.getEncodedPath());
        }
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL2, this.mHeader);
        values.put(DatabaseConstants.COLUMN_NAME_GENERAL3, this.mCustomData);
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
        String acqUriString = c.getString(DatabaseConstants.COLUMN_ACQ_URI);
        this.mCustomData = c.getString(DatabaseConstants.COLUMN_ACQ_CUSTOMDATA);
        this.mHeader = c.getString(DatabaseConstants.COLUMN_ACQ_HEADER);
        if (acqUriString != null) {
            this.mFileUri = Uri.parse(acqUriString);
        } else {
            this.mFileUri = null;
        }
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_POS_GRP_ID));
        return true;
    }

}
