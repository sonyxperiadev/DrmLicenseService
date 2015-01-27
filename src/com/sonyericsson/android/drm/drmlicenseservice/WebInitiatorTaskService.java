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

package com.sonyericsson.android.drm.drmlicenseservice;

import com.sonyericsson.android.drm.drmlicenseservice.DLSHttpClient.Response;
import com.sonyericsson.android.drm.drmlicenseservice.DLSHttpClient.RetryCallback;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;

public class WebInitiatorTaskService extends IntentService {

    private Uri mUri = null;

    private Context mContext;

    private long mSessionId;

    private RetryCallback mRetryCallback;

    private static final String
            WEBI_TYPE_LICENSE_ACQUISITION = "LicenseAcquisition",
            WEBI_TYPE_JOIN_DOMAIN = "JoinDomain",
            WEBI_TYPE_LEAVE_DOMAIN = "LeaveDomain",
            WEBI_LICENSE_ACQUISITION_KID = "KID",
            WEBI_LICENSE_ACQUISITION_CONTENT = "Content",
            WEBI_LICENSE_ACQUISITION_HEADER = "Header";

    public WebInitiatorTaskService() {
        super("WebInitiatorTaskService");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        DrmLog.debug("onHandleIntent");
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mSessionId = extras.getLong(Constants.DLS_INTENT_SESSION_ID,
                    Constants.NOT_AIDL_SESSION);
            DrmLog.debug("sessionId " + mSessionId);
            SessionManager.getInstance().makeSureAIDLSessionIsOpen(mSessionId);
        }
        mContext = getBaseContext();
        mUri = intent.getData();
        mRetryCallback = new RetryCallback() {

            @Override
            public void retryingUrl(int httpError, int innerHttpError, String url) {
                Bundle parameters = new Bundle();
                parameters.putString(Constants.DRM_KEYPARAM_URL, url);
                parameters.putInt(Constants.DRM_KEYPARAM_HTTP_ERROR, httpError);
                parameters.putInt(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR, innerHttpError);
                SessionManager.getInstance().callback(mSessionId,
                        Constants.PROGRESS_TYPE_HTTP_RETRYING, true, parameters);
            }

        };
        execute();
    }

    /**
     * Trigger execution of WebInitiatorManager
     */
    public void execute() {
        boolean status = false;
        int httpError = 0;
        int innerHttpError = 0;

        WebinitiatorData webi = getWebinitiator();
        int dataLength = (webi.data != null) ? webi.data.length() : 0;
        if (dataLength > 0) {
            ArrayDeque<HashMap<String, String>> dataAll = XmlParser.parseWebInitiator(webi.data);
            if (dataAll != null) {
                int numberOfGroups = dataAll.size();
                if (mSessionId > 0) {
                    Bundle callbackParameters = new Bundle();
                    callbackParameters.putString(Constants.DRM_KEYPARAM_WEB_INITIATOR,
                            mUri.toString());
                    callbackParameters.putString(Constants.DLS_CB_PATH, mUri.toString());
                    callbackParameters.putInt(Constants.DRM_KEYPARAM_GROUP_COUNT, numberOfGroups);
                    // callback, notify number of jobs
                    SessionManager.getInstance().callback(mSessionId,
                            Constants.PROGRESS_TYPE_WEBINI_COUNT, true, callbackParameters);
                }
                // Loop through the parts of the initiator (it may
                // be multiple parts).
                int groupId = 0;
                while (dataAll.size() > 0 && !SessionManager.getInstance().isCancelled(mSessionId)) {
                    groupId++;
                    handleInitiatorDataItem(dataAll.removeFirst(), numberOfGroups, groupId);
                }
                status = true;
            } else {
                httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
            }
        } else {
            httpError = webi.httpError;
            innerHttpError = webi.innerHttpError;
        }
        if (mSessionId > Constants.NOT_AIDL_SESSION) { // has callback handler
            if (httpError != 0) { // parsing error
                Bundle parameters = new Bundle();
                parameters.putInt(Constants.DRM_KEYPARAM_GROUP_COUNT, 0);
                if (mUri != null) {
                    parameters.putString(Constants.DRM_KEYPARAM_WEB_INITIATOR, mUri.toString());
                }
                parameters.putInt(Constants.DRM_KEYPARAM_HTTP_ERROR, httpError);
                if (innerHttpError != 0) {
                    parameters.putInt(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR, innerHttpError);
                }
                SessionManager.getInstance().callback(mSessionId,
                        Constants.PROGRESS_TYPE_WEBINI_COUNT, false, parameters);
            }
            Intent finishedIntent = new Intent(Constants.TASK_SERVICE);
            finishedIntent.setClass(mContext, DrmLicenseTaskService.class);
            finishedIntent.putExtra(Constants.DLS_INTENT_TYPE,
                    Constants.DLS_INTENT_TYPE_FINISHED_WEBI);
            finishedIntent.putExtra(Constants.DLS_INTENT_SESSION_ID, mSessionId);
            mContext.startService(finishedIntent);
        }

        if (status && mUri != null && (ContentResolver.SCHEME_FILE.equals(mUri.getScheme()) ||
                ContentResolver.SCHEME_CONTENT.equals(mUri.getScheme())) &&
                !new File(mUri.getPath()).delete()) {
            DrmLog.debug("Failed to remove executed webinitiator");
        }
    }

    private WebinitiatorData getWebinitiator() {
        String respData = null;
        int httpError = 0, innerHttpError = 0;
        if (mUri != null) {
            String host;
            if (("http".equals(mUri.getScheme()) || "https".equals(mUri.getScheme())) &&
                    ((host = mUri.getHost()) != null) && host.length() > 0) {
                Response response = DLSHttpClient.get(mContext, mSessionId, mUri.toString(),
                        mRetryCallback);

                if (response != null && response.getStatus() == 200) {
                    respData = response.getData();
                    if (respData == null || respData.length() == 0) {
                        DrmLog.debug("Request to " + mUri.toString() + " did not return any data.");
                        httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                    }
                } else {
                    DrmLog.debug("Request to " + mUri.toString() + " failed.");
                    if (response != null) {
                        httpError = response.getStatus();
                        innerHttpError = response.getInnerStatus();
                    }
                }
            } else if (ContentResolver.SCHEME_FILE.equals(mUri.getScheme())
                    || ContentResolver.SCHEME_CONTENT.equals(mUri.getScheme())) {
                // This will only happen if file is loaded from Download List
                String path = null;
                if (ContentResolver.SCHEME_CONTENT.equals(mUri.getScheme())) {
                    String[] projection = new String[] {
                        MediaStore.MediaColumns.DATA
                    };
                    Cursor cursor = null;
                    try {
                        cursor = mContext.getContentResolver()
                                .query(mUri, projection, null, null, null);
                        if (null == cursor || 0 == cursor.getCount() || !cursor.moveToFirst()) {
                            httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                        } else {
                            int pathIndex = cursor
                                    .getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                            path = cursor.getString(pathIndex);
                        }
                    } catch (SecurityException e) {
                        httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                        DrmLog.logException(e);
                    } finally {
                        if (null != cursor) {
                            cursor.close();
                        }
                    }
                } else {
                    // In case scheme equals SCHEME_FILE
                    path = mUri.getPath();
                }
                if (null != path) {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(path);
                        StringBuilder buf = new StringBuilder();
                        while (fis.available() > 0) {
                            byte data[] = new byte[1024];
                            int count = fis.read(data);
                            buf.append(new String(data, 0, count, "UTF-8"));
                        }
                        respData = buf.toString();
                    } catch (IOException e) {
                        httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                        DrmLog.logException(e);
                    } finally {
                        try {
                            if (fis != null) {
                                fis.close();
                            }
                        } catch (IOException e) {}
                    }
                } else {
                    httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                }
            } else {
                // Unsupported scheme in Uri
                httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
            }
        } else {
            httpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
        }
        return new WebinitiatorData(respData, httpError, innerHttpError);
    }

    private void handleInitiatorDataItem(HashMap<String, String> data, int numberOfGroups,
            int groupId) {
        String type = data.get("type");
        Bundle callbackParameters = new Bundle();
        Intent intent = new Intent(Constants.TASK_SERVICE);
        intent.setClass(mContext, DrmLicenseTaskService.class);
        intent.putExtra(Constants.DLS_INTENT_TYPE, Constants.DLS_INTENT_TYPE_TASK);
        intent.putExtra(Constants.DLS_INTENT_ITEMDATA, data);
        intent.putExtra(Constants.DLS_INTENT_SESSION_ID, mSessionId);
        callbackParameters.putInt(Constants.DRM_KEYPARAM_GROUP_COUNT, numberOfGroups);
        callbackParameters.putInt(Constants.DRM_KEYPARAM_GROUP_NUMBER, groupId);
        callbackParameters.putInt(Constants.DLS_CB_PROGRESS_TYPE,
                Constants.PROGRESS_TYPE_FINISHED_JOB);
        if (type.equals(WEBI_TYPE_LICENSE_ACQUISITION)) {
            String kid = data.get(WEBI_LICENSE_ACQUISITION_KID);
            String content = data.get(WEBI_LICENSE_ACQUISITION_CONTENT);
            String header = data.get(WEBI_LICENSE_ACQUISITION_HEADER);
            if (header != null && header.length() > 0 && kid != null && kid.length() > 0) {
                if (content != null && content.length() > 0) {
                    callbackParameters.putString(Constants.DLS_CB_PATH, content);
                    if (mSessionId == Constants.NOT_AIDL_SESSION
                            || !SessionManager.getInstance().hasCallbackHandler(mSessionId)) {
                        ContentDownloadManager dlManager = new ContentDownloadManager(mContext,
                                content, mSessionId);
                        Thread t = new Thread(dlManager);
                        t.start();
                    }
                }
                intent.putExtra(Constants.DLS_INTENT_REQUEST_TYPE,
                        RequestManager.TYPE_ACQUIRE_LICENSE);
                callbackParameters.putString(Constants.DRM_KEYPARAM_TYPE,
                        Constants.AIDL_CB_TYPE_ACQUIRE_LICENSE);
            } else {
                DrmLog.debug("Missing or incorrect Header/Kid");
            }
        } else if (type.equals(WEBI_TYPE_JOIN_DOMAIN)) {
            intent.putExtra(Constants.DLS_INTENT_REQUEST_TYPE, RequestManager.TYPE_JOIN_DOMAIN);
            callbackParameters.putString(Constants.DRM_KEYPARAM_TYPE,
                    Constants.AIDL_CB_TYPE_JOIN_DOMAIN);
        } else if (type.equals(WEBI_TYPE_LEAVE_DOMAIN)) {
            intent.putExtra(Constants.DLS_INTENT_REQUEST_TYPE, RequestManager.TYPE_LEAVE_DOMAIN);
            callbackParameters.putString(Constants.DRM_KEYPARAM_TYPE,
                    Constants.AIDL_CB_TYPE_LEAVE_DOMAIN);
        } else {
            // Sending intent without, "DLS_INTENT_REQUEST_TYPE", will be
            // treated as error in request manager,finished job will still be
            // reported. (AKA forceFailureJob)
            callbackParameters.putString(Constants.DRM_KEYPARAM_TYPE, type);
            DrmLog.debug("Unknown initiator: " + type);
        }
        intent.putExtra(Constants.DLS_INTENT_CB_PARAMS,callbackParameters);
        mContext.startService(intent);
    }

    private static class WebinitiatorData {
        String data = null;
        int httpError = 0;
        int innerHttpError = 0;

        private WebinitiatorData (String respData, int error, int innerError) {
            data = respData;
            httpError = error;
            innerHttpError = innerError;
        }
    }
}
