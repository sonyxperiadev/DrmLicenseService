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


import android.content.Context;
import android.media.MediaDrm;
import android.media.MediaDrm.KeyRequest;
import android.os.Bundle;

import com.sonyericsson.android.drm.drmlicenseservice.DLSHttpClient.RetryCallback;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Stack;

/**
 * Class to handle requests between PortingKit and RightsManager
 *
 */
public class RequestManager {

    private MediaDrm mMediaDrm = null;
    private byte[] mSessionId = null;
    private Stack<Task> mTasks = null;
    private RedirectCallback mCallback;
    private Context mContext;
    private int mOriginalType;

    private static final String
            ERROR_DOMAIN_REQUIRED = "0x8004c605",
            ERROR_RENEW_DOMAIN = "0x8004c606",
            ERROR_SERVER_SPECIFIC ="0x8004c604";

    public static final int
            TYPE_ACKNOWLEDGE_LICENSE = 0,
            TYPE_ACQUIRE_LICENSE = 1,
            TYPE_JOIN_DOMAIN = 2,
            TYPE_LEAVE_DOMAIN = 3,
            TYPE_RENEW_RIGHTS = 4;
    private static final int TYPE_DIFF = 100;
    private static final int
            PROCESS_TYPE_ACKNOWLEDGE_LICENSE = TYPE_ACKNOWLEDGE_LICENSE + TYPE_DIFF,
            PROCESS_TYPE_JOIN_DOMAIN = TYPE_JOIN_DOMAIN + TYPE_DIFF,
            PROCESS_TYPE_LEAVE_DOMAIN = TYPE_LEAVE_DOMAIN + TYPE_DIFF;

    private static final String
            STATUS_CODE = "StatusCode",
            REDIRECT_URL = "RedirectUrl",
            CUSTOMDATA = "CustomData",
            REVISION = "Revision",
            ACCOUNT_ID = "AccountId",
            SERVICE_ID = "ServiceId",
            WEBI_ACCOUNT_ID = "AccountID",
            WEBI_DS_ID = "DS_ID",
            WEBI_DOMAIN_CONTROLLER = "DomainController",
            WEBI_LUI_URL = "LUI_URL",
            WEBI_HEADER = "Header";

    public boolean isOk = true;

    /**
     * Interface to notify service of redirect in case there is no connection
     * from AIDL for session
     */
    interface RedirectCallback {

        /**
         * Notify service to redirect towards specific URL
         *
         * @param url target of redirection
         */
        public void onLaunchLuiUrl(String url);

    }

    /**
     * Creates new RequestManager, parameters need to contain request type and
     * certain values needed for such request, see Task constructor.
     *
     * @param parameters for request and callback
     * @param callback for redirect information
     */
    public RequestManager(Context context, Bundle parameters, RedirectCallback callback) {
        mCallback = callback;
        mContext = context;
        try {
            mTasks = new Stack<Task>();
            if (parameters != null) {
                mOriginalType = parameters.getInt(Constants.DLS_INTENT_REQUEST_TYPE, -1);
                mTasks.add(new Task(parameters));
                if (!SessionManager.getInstance().isCancelled(mTasks.peek().mDlsSessionId)) {
                    mMediaDrm = new MediaDrm(Constants.UUID_PR);
                    mSessionId = mMediaDrm.openSession();
                }
            } else {
                DrmLog.debug("Error request parameters is null");
            }
        } catch (Exception e) {
            DrmLog.logException(e);
        }
    }

    /**
     * Trigger request
     */
    public void execute() {
        Task currentTask = null;
        while (!mTasks.isEmpty()) {
            currentTask = mTasks.pop();
            if (isOk && !SessionManager.getInstance().isCancelled(currentTask.mDlsSessionId)) {
                isOk = false;
                KeyRequest keyRequest = prepareRequest(currentTask);
                processKeyRequest(keyRequest, currentTask);
                currentTask.report();
            }
        }

        // NOTE, We only check for renewRights calls here since WebInitiators
        // can contain several task so they are only safe to clear when
        // DLS_INTENT_TYPE_FINISHED_WEBI are processed in TaskService
        if (mOriginalType == TYPE_RENEW_RIGHTS &&
                SessionManager.getInstance().isCancelled(currentTask.mDlsSessionId)) {
            // Notify that session cancel is now complete.
            SessionManager.getInstance().callback(currentTask.mDlsSessionId,
                    Constants.PROGRESS_TYPE_CANCELLED, true, new Bundle());
            // Cancelled renewRights sessions are now safe to be cleared from list.
            SessionManager.getInstance().clearCancelled(currentTask.mDlsSessionId);
        }

        if (mMediaDrm != null && mSessionId != null) {
            mMediaDrm.closeSession(mSessionId);
        }
    }

    /**
     * prepares request to PK
     */
    private KeyRequest prepareRequest(Task task) {
        DrmLog.debug("prepareRequest, type: " + task.type);
        HashMap<String, String> request = new HashMap<String, String>();
        String customData;
        switch (task.type) {
            case TYPE_ACKNOWLEDGE_LICENSE:
                request.put(Constants.DRM_DATA, task.mLastHttpResponseData);
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_LIC_RESPONSE);
                break;
            case TYPE_ACQUIRE_LICENSE:
                if (task.mHeader == null) {
                    return null;
                } else if (!XmlParser.isValidXml(task.mHeader)) {
                    task.mHttpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                    return null;
                }
                request.put(Constants.DRM_HEADER, task.mHeader);
                if ((customData = task.getCustomData())  != null) {
                    request.put(Constants.DRM_CUSTOM_DATA, customData);
                }
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GENERATE_LIC_CHALLENGE);
                break;
            case TYPE_JOIN_DOMAIN:
                if (task.mServiceId.equals(Constants.ALL_ZEROS_DRM_ID) &&
                        task.mAccountId.equals(Constants.ALL_ZEROS_DRM_ID)) {
                    task.mHttpError = Constants.HTTP_ERROR_INTERNAL_ERROR;
                    return null;
                }
                request.put(Constants.DRM_DOMAIN_SERVICE_ID, task.mServiceId);
                request.put(Constants.DRM_DOMAIN_ACCOUNT_ID, task.mAccountId);
                String friendlyName = task.getParam(Constants.DRM_KEYPARAM_FRIENDLY_NAME);
                if (friendlyName != null) {
                    request.put(Constants.DRM_DOMAIN_FRIENDLY_NAME, friendlyName);
                }
                if (task.mUrlUsed != null) {
                    request.put(REDIRECT_URL, task.mUrlUsed);
                }
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GENERATE_JOIN_DOM_CHALLENGE);
                request.put(Constants.DRM_DOMAIN_REVISION, task.mRevision);
                if ((customData = task.getCustomData()) != null) {
                    request.put(Constants.DRM_CUSTOM_DATA, customData);
                }
                break;
            case TYPE_LEAVE_DOMAIN:
                if (task.mServiceId.equals(Constants.ALL_ZEROS_DRM_ID) &&
                    task.mAccountId.equals(Constants.ALL_ZEROS_DRM_ID)) {
                    return null;
                }
                request.put(Constants.DRM_DOMAIN_SERVICE_ID, task.mServiceId);
                request.put(Constants.DRM_DOMAIN_ACCOUNT_ID, task.mAccountId);
                request.put(Constants.DRM_DOMAIN_REVISION, task.mRevision);
                if ((customData = task.getCustomData()) != null) {
                    request.put(Constants.DRM_CUSTOM_DATA, customData);
                }
                request.put(Constants.DRM_ACTION,
                        Constants.DRM_ACTION_GENERATE_LEAVE_DOM_CHALLENGE);
                break;
            case PROCESS_TYPE_JOIN_DOMAIN:
                request.put(Constants.DRM_DATA, task.mLastHttpResponseData);
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_JOIN_DOM_RESPONSE);
                break;
            case PROCESS_TYPE_LEAVE_DOMAIN:
                request.put(Constants.DRM_DATA, task.mLastHttpResponseData);
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_LEAVE_DOM_RESPONSE);
                break;
            case PROCESS_TYPE_ACKNOWLEDGE_LICENSE:
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_PROCESS_LIC_ACK_RESPONSE);
                request.put(Constants.DRM_DATA, task.mLastHttpResponseData);
                break;
            default:
                DrmLog.debug("Error no such case (forceFailureJob)");
                return null;
        }
        DrmLog.debug(request.toString());
        return createKeyRequest(request);
    }

    /**
     * sends request to PK, returns service request
     * both processing of last request and generating of possible response
     */
    private KeyRequest createKeyRequest(HashMap<String, String> parameters) {
        DrmLog.debug("createKeyRequest");
        KeyRequest keyRequest = null;
        try {
            keyRequest = mMediaDrm.getKeyRequest(mSessionId, null, null, MediaDrm.KEY_TYPE_OFFLINE,
                    parameters);
        } catch (Exception e) {
            DrmLog.logException(e);
        }
        return keyRequest;
    }

    /**
     * Handle keyRequest response from PK, if "ok"/"fail" from PK were done.
     */
    private void processKeyRequest(KeyRequest keyRequest, Task currentTask) {
        DrmLog.debug("processKeyRequest");
        currentTask.lastType = currentTask.type;
        if (keyRequest != null) {
            byte[] data = keyRequest.getData();
            if (data != null && data.length > 0) {
                String dataString = null;
                try {
                    dataString = new String(data, "UTF-8");
                    DrmLog.debug(dataString);
                    if (dataString.equals("ok") || dataString.startsWith("fail")) {
                        if (!dataString.equals("ok")) {
                            if (dataString.length() > 4) {
                                String errorCode =
                                        dataString.substring(dataString.indexOf(",")+1);
                                currentTask.mInnerHttpError = Long.decode(errorCode).intValue();
                            }
                            currentTask.mHttpError = Constants.HTTP_ERROR_UNHANDLED_ERROR_IN_PK;
                            tryRedirect(currentTask);
                        } else {
                            // task finished successfully
                            isOk = true;
                        }
                    } else {
                        if (currentTask.mUrlUsed == null) {
                            currentTask.mUrlUsed = keyRequest.getDefaultUrl();
                        }
                        String url = currentTask.mUrlUsed;
                        DrmLog.debug("Not done, execute post, towards: " + url);
                        processHttpResponse(
                                DLSHttpClient.post(mContext, currentTask.mDlsSessionId, url,
                                        getType(currentTask.type), dataString,
                                        currentTask.retryCallback,
                                        currentTask.type == TYPE_ACQUIRE_LICENSE), currentTask);
                    }
                } catch (UnsupportedEncodingException e) {
                    DrmLog.debug("nonsupported encoding in datastring");
                    currentTask.mHttpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                }
            } else {
                DrmLog.debug("processKeyRequest, no data");
                currentTask.mHttpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
            }
        } else {
            DrmLog.debug("missing parameters for prepareKeyRequest");
        }
    }

    private String getType(int type) {
        String soapAction = "";
        switch (type) {
            case TYPE_ACKNOWLEDGE_LICENSE:
                soapAction = "AcknowledgeLicense";
                break;
            case TYPE_RENEW_RIGHTS:
            case TYPE_ACQUIRE_LICENSE:
                soapAction = "AcquireLicense";
                break;
            case TYPE_JOIN_DOMAIN:
                soapAction = "JoinDomain";
                break;
            case TYPE_LEAVE_DOMAIN:
                soapAction = "LeaveDomain";
                break;
            default:
        }
        return soapAction;
    }

    private void tryRedirect(Task task) {
        if (task.mLuiUrl == null) {
            task.parseForLuiUrl();
        }
        if (task.mDlsSessionId > Constants.NOT_AIDL_SESSION &&
                SessionManager.getInstance().hasCallbackHandler(task.mDlsSessionId)) {
            if (task.mLuiUrl != null && task.mRedirectURL == null) {
                task.mRedirectURL = task.mLuiUrl;
            }
        } else {
            if (task.mLuiUrl != null) {
                if (mCallback != null) {
                    mCallback.onLaunchLuiUrl(task.mLuiUrl);
                }
            }
        }
    }

    private void processHttpResponse(DLSHttpClient.Response httpResponse, Task currentTask) {
        DrmLog.debug("processHttpResponse");
        if (httpResponse != null) {
            switch (httpResponse.getStatus()) {
                case 200:
                    currentTask.mLastHttpResponseData = httpResponse.getData();
                    if (currentTask.mLastHttpResponseData != null) {
                        currentTask.mCustomDataInResponse =
                                XmlParser.parseXml(currentTask.mLastHttpResponseData, CUSTOMDATA);
                        switch (currentTask.type) {
                            case TYPE_ACQUIRE_LICENSE:
                                currentTask.type = TYPE_ACKNOWLEDGE_LICENSE;
                                break;
                            default:
                                currentTask.type += TYPE_DIFF;
                        }
                        mTasks.add(currentTask);
                        isOk = true;
                    } else {
                        currentTask.mHttpError = Constants.HTTP_ERROR_INTERNAL_ERROR;
                    }
                    break;
                case 500:
                    HashMap<String, String> errorData = XmlParser.parseXml(httpResponse.getData());
                    if (errorData != null) {
                        String errorCode = errorData.get(STATUS_CODE);
                        String redirectUrl = errorData.get(REDIRECT_URL);
                        if (currentTask.type == TYPE_ACQUIRE_LICENSE) {
                            if ((ERROR_DOMAIN_REQUIRED.equals(errorCode) ||
                                    ERROR_RENEW_DOMAIN.equals(errorCode))) {
                                if (!currentTask.triedJoinDomain) {
                                    currentTask.triedJoinDomain = true;
                                    currentTask.lastType = -1; // reset task
                                    mTasks.add(currentTask);
                                    Task joinDomainTask = currentTask.deriveDomainJob(
                                            ERROR_RENEW_DOMAIN.equals(errorCode), errorData);
                                    mTasks.add(joinDomainTask);
                                    isOk = true;
                                }
                            } else if (ERROR_SERVER_SPECIFIC.equals(errorCode)) {
                                tryRedirect(currentTask);
                            }
                        }
                        if (!isOk) {
                            // only add error codes if error is not related to domain,
                            // or if we already tried JoinDomain
                            currentTask.mHttpError = httpResponse.getStatus();
                            if (errorCode != null) {
                                currentTask.mInnerHttpError = Long.decode(errorCode).intValue();
                            }
                            if (redirectUrl != null && redirectUrl.length() > 0) {
                                currentTask.mRedirectURL = redirectUrl;
                            }
                        }
                    } else {
                        currentTask.mHttpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
                        currentTask.mInnerHttpError = httpResponse.getStatus();
                    }
                    break;
                case 301:
                case 302:
                case 303:
                case 307:
                    currentTask.mRedirectCount++;
                    if (currentTask.mRedirectCount > currentTask.getParam(
                            Constants.DRM_KEYPARAM_REDIRECT_LIMIT, 20)) {
                        currentTask.mHttpError = Constants.HTTP_ERROR_TOO_MANY_REDIRECTS;
                        currentTask.mInnerHttpError = httpResponse.getStatus();
                    } else {
                        if (httpResponse.mRedirect != null) {
                            String pre = "<LA_URL>", post = "</LA_URL>";
                            int start = currentTask.mHeader.indexOf(pre) + pre.length();
                            int end = currentTask.mHeader.indexOf(post);
                            String currentUrl = currentTask.mHeader.substring(start, end);
                            // Apply XML character-entity encoding to '&' in redirect url
                            String newUrl = httpResponse.mRedirect.replaceAll("&(?!amp;)", "&amp;");
                            currentTask.mUrlUsed = httpResponse.mRedirect;
                            currentTask.mHeader = currentTask.mHeader.replace(pre + currentUrl
                                    + post, pre + newUrl + post);
                            currentTask.lastType = -1; // reset task
                            mTasks.add(currentTask);
                            isOk = true;
                        }
                    }
                    break;
                default:
                    DrmLog.debug("Unexpected case, statusCode: " + httpResponse.getStatus());
                    currentTask.mHttpError = httpResponse.getStatus();
                    currentTask.mInnerHttpError = httpResponse.getInnerStatus();
                    // Request towards LA_URL returned error, check for LUI_URL
                    tryRedirect(currentTask);
            }
        } else {
            DrmLog.debug("no response from DLSHttpClient");
            if (SessionManager.getInstance().isCancelled(currentTask.mDlsSessionId)) {
                currentTask.mHttpError = Constants.HTTP_ERROR_CANCELLED;
            }
        }
        DrmLog.debug("processHttpResponse end " + currentTask.mHttpError + "  " +
                currentTask.mInnerHttpError);
    }

    /**
     * Class defining part of a complete request.
     * Holds state and parameters.
     */
    class Task {

        public RetryCallback retryCallback;
        public boolean triedJoinDomain = false;
        public int type = -1, lastType = -1;
        public boolean isDerived = false;

        public String mServiceId = Constants.ALL_ZEROS_DRM_ID;
        public String mAccountId = Constants.ALL_ZEROS_DRM_ID;
        public String mRevision = "0";
        public String mLastHttpResponseData;
        public String mHeader = null;

        public String mUrlUsed = null;
        public String mParsedCustomData = null;
        public String mCustomDataInResponse = null;
        public String mLuiUrl = null;
        public String mRedirectURL = null;
        public int mRedirectCount = 0;

        public long mDlsSessionId = 0;
        public Bundle mCallbackParameters = null;
        public int mHttpError = 0;
        public int mInnerHttpError = 0;

        private Task(int type) {
            this.type = type;
        }

        private Task(Bundle taskParams) {
            this.type = taskParams.getInt(Constants.DLS_INTENT_REQUEST_TYPE, -1);
            this.mDlsSessionId = taskParams.getLong(Constants.DLS_INTENT_SESSION_ID,
                    Constants.NOT_AIDL_SESSION);
            this.mCallbackParameters = taskParams.getBundle(Constants.DLS_INTENT_CB_PARAMS);

            @SuppressWarnings("unchecked")
            HashMap<String, String> data = (HashMap<String, String>)
                    taskParams.getSerializable(Constants.DLS_INTENT_ITEMDATA);
            if (data == null) {
                data = new HashMap<String, String>();
            }

            String tmp;
            switch (type) {
                case TYPE_RENEW_RIGHTS:
                    if (taskParams.containsKey(Constants.DRM_KEYPARAM_RENEW_HEADER)) {
                        mHeader = taskParams.getString(Constants.DRM_KEYPARAM_RENEW_HEADER);
                    } else if (taskParams.containsKey(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX)) {
                        HeaderExtractor.parsePSSH(
                                taskParams.getByteArray(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX),
                                this);
                    } else {
                        HeaderExtractor.parseFile(mContext,
                                taskParams.getString(Constants.DRM_KEYPARAM_RENEW_FILE_URI), this);
                    }
                    this.type = TYPE_ACQUIRE_LICENSE;
                    break;
                case TYPE_ACQUIRE_LICENSE:
                    mHeader = data.get(WEBI_HEADER);
                    mParsedCustomData = data.get(CUSTOMDATA);
                    mLuiUrl = data.get(WEBI_LUI_URL);
                    break;
                case TYPE_JOIN_DOMAIN:
                    mUrlUsed = data.get(WEBI_DOMAIN_CONTROLLER);
                    tmp = data.get(WEBI_DS_ID);
                    mServiceId = (tmp != null) ? tmp : Constants.ALL_ZEROS_DRM_ID;
                    tmp = data.get(WEBI_ACCOUNT_ID);
                    mAccountId = (tmp != null) ? tmp : Constants.ALL_ZEROS_DRM_ID;
                    tmp = data.get(REVISION);
                    mRevision = (tmp != null) ? tmp : mRevision;
                    tmp = data.get(CUSTOMDATA);
                    mParsedCustomData = (tmp != null) ? tmp : null;
                    break;
                case TYPE_LEAVE_DOMAIN:
                    mUrlUsed = data.get(WEBI_DOMAIN_CONTROLLER);
                    tmp = data.get(WEBI_DS_ID);
                    mServiceId = (tmp != null) ? tmp : Constants.ALL_ZEROS_DRM_ID;
                    tmp = data.get(WEBI_ACCOUNT_ID);
                    mAccountId = (tmp != null) ? tmp : Constants.ALL_ZEROS_DRM_ID;
                    tmp = data.get(REVISION);
                    mRevision = (tmp != null) ? tmp : mRevision;
                    tmp = data.get(CUSTOMDATA);
                    mParsedCustomData = (tmp != null) ? tmp : null;
                    break;
                default:
                    DrmLog.debug("Error no such case (forceFailureJob)");
            }
            setRetryCallback();
        }

        private void setRetryCallback() {
            final long fSessionId = mDlsSessionId;
            retryCallback = new RetryCallback() {

                @Override
                public void retryingUrl(int httpError, int innerHttpError, String url) {
                    if (fSessionId > Constants.NOT_AIDL_SESSION) {
                        Bundle parameters = (mCallbackParameters == null) ? new Bundle()
                                : (Bundle)mCallbackParameters.clone();
                        parameters.putString(Constants.DRM_KEYPARAM_URL, url);
                        parameters.putInt(Constants.DRM_KEYPARAM_HTTP_ERROR, httpError);
                        parameters.putInt(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR, innerHttpError);
                        SessionManager.getInstance().callback(fSessionId,
                                Constants.PROGRESS_TYPE_HTTP_RETRYING, true, parameters);
                    }
                }

            };
        }

        /*
         * Create a joinDomain task from parameters in current task
         *
         * NOTE: in case derived task fails we need to report that task, therefore we clone
         * callback parameters so we get correct GroupId and Groups count
         */
        private Task deriveDomainJob(boolean renew, HashMap<String, String> errorData) {
            Task joinDomainTask = new Task(TYPE_JOIN_DOMAIN);
            joinDomainTask.mHeader = mHeader;
            joinDomainTask.mDlsSessionId = mDlsSessionId;
            if (mCallbackParameters != null) {
                joinDomainTask.mCallbackParameters = (Bundle)mCallbackParameters.clone();
                joinDomainTask.mCallbackParameters.putString(Constants.DRM_KEYPARAM_TYPE,
                        Constants.AIDL_CB_TYPE_JOIN_DOMAIN);
            }
            joinDomainTask.isDerived  = true;
            joinDomainTask.setRetryCallback();
            joinDomainTask.parseErrorData(renew, errorData);
            return joinDomainTask;
        }

        private String getParam(String parameterkey) {
            String res = null;
            Bundle httpParameters = SessionManager.getInstance().getHttpParams(mDlsSessionId);
            if (httpParameters != null) {
                res = httpParameters.getString(parameterkey, null);
            }
            return res;
        }

        private int getParam(String parameterkey, int defaultValue) {
            int res = defaultValue;
            Bundle httpParameters = SessionManager.getInstance().getHttpParams(mDlsSessionId);
            if (httpParameters != null) {
                res = httpParameters.getInt(parameterkey, defaultValue);
            }
            return res;
        }

        /*
         * Needs it own function since we need to check
         * both customdata from webinitiator and parameters.
         */
        private String getCustomData() {
            StringBuilder buffer = new StringBuilder();
            String res = null;
            String customDataFromParams = this.getParam(Constants.DRM_KEYPARAM_CUSTOM_DATA);
            if (customDataFromParams != null) {
                String tmp = this.getParam(Constants.DRM_KEYPARAM_CUSTOM_DATA_PREFIX);
                if (tmp != null) {
                    buffer.append(tmp);
                }
                buffer.append(customDataFromParams);
                tmp = this.getParam(Constants.DRM_KEYPARAM_CUSTOM_DATA_SUFFIX);
                if (tmp != null) {
                    buffer.append(tmp);
                }
            } else {
                if(mParsedCustomData != null){
                    buffer.append(mParsedCustomData);
                }
            }
            if(buffer.length() > 0){
                res = buffer.toString();
            }
            return res;
        }

        private void parseErrorData(boolean renew, HashMap<String, String> errorData) {
            mServiceId = errorData.get(SERVICE_ID);
            if (mServiceId == null) {
                mServiceId = Constants.ALL_ZEROS_DRM_ID;
            }
            mAccountId = errorData.get(ACCOUNT_ID);
            if (mAccountId == null) {
                mAccountId = Constants.ALL_ZEROS_DRM_ID;
            }
            mUrlUsed = (errorData.get(REDIRECT_URL) != null) ? errorData
                    .get(REDIRECT_URL) : mUrlUsed;
            mRedirectURL = mUrlUsed; // Redirect url for callback

            String customData = errorData.get(CUSTOMDATA);
            if (renew) {
                mRevision = (customData != null) ? customData : mRevision;
            } else {
                mRevision = (errorData.get(REVISION) != null) ? errorData.get(REVISION) : mRevision;
                mParsedCustomData = (customData != null) ? customData : mParsedCustomData;
            }
        }

        private void report() {
            DrmLog.debug("Report, type: " + type + ", lastType: " + lastType + ", STATUS: "
                    + isOk + ", HTML_ERROR: " + mHttpError + ", INNER_HTML_ERROR: " +
                    mInnerHttpError);
            /*
             * NOTE: check that we are in a valid state for callback. Need to
             * check that type == lastType, otherwise we got a new request from
             * PK to send. Also, don't report derived tasks, unless they failed.
             */
            if (type != lastType || (isOk && isDerived)) {
                DrmLog.debug("Nothing to report");
                return;
            }

            // Only tasks with sessionIDs greater than 0 have callbackReceivers
            if (mDlsSessionId > Constants.NOT_AIDL_SESSION &&
                    mHttpError != Constants.HTTP_ERROR_CANCELLED) {
                if (mCallbackParameters == null) {
                    mCallbackParameters = new Bundle();
                }
                int state = mCallbackParameters.getInt(Constants.DLS_CB_PROGRESS_TYPE,
                        Constants.PROGRESS_TYPE_FINISHED_JOB);

                if (mCustomDataInResponse != null) {
                    mCallbackParameters.putString(Constants.DRM_KEYPARAM_CUSTOM_DATA,
                            mCustomDataInResponse);
                }
                if (mRedirectURL != null) {
                    mCallbackParameters.putString(Constants.DRM_KEYPARAM_REDIRECT_URL,
                            mRedirectURL);
                }
                mCallbackParameters.putInt(Constants.DRM_KEYPARAM_HTTP_ERROR, mHttpError);
                mCallbackParameters.putInt(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR,
                        mInnerHttpError);
                SessionManager.getInstance().callback(mDlsSessionId, state, isOk,
                        mCallbackParameters);
            }
        }

        private void parseForLuiUrl() {
            if (mHeader != null) {
                mLuiUrl = XmlParser.parseXml(mHeader, "LUI_URL");
            }
        }
    }
}
