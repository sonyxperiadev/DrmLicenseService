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
 * Portions created by Sony Mobile Communications Inc. are Copyright (C) 2014
 * Sony Mobile Communications Inc. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.util.ByteArrayBuffer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.LongSparseArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DLSHttpClient {

    private static final LongSparseArray<HttpRequest> mIdMap =
            new LongSparseArray<DLSHttpClient.HttpRequest>(1);

    /**
     * Optional interface to implement by clients, if they need data during
     * download
     */
    public interface DataHandlerCallback {

        /**
         * Perform optional data handling in client during download.
         *
         * @param is containing downloaded data
         */
        public void handleData(InputStream is);
    }

    /**
     * Optional interface to implement by clients, if they need information
     * about when http requests are retried
     */
    public interface RetryCallback {

        /**
         *  Provides additional feedback to the client when a http retry
         *  is made. In this way it will be possible for client application
         *  to provide better feedback to the end user.
         *
         * @param httpError see IDrmLicenseServiceCallback.aidl for description
         * @param innerHttpError see IDrmLicenseServiceCallback.aidl for description
         * @param url target of request
         */
        public void retryingUrl(int httpError, int innerHttpError, String url);
    }

    public static class Response {
        private int mStatus = 0;

        private int mInnerStatus = 0;

        private String mMime = null;

        private String mData = null;

        public String mRedirect = null;

        public Response(int status, int innerStatus, String mime, String data) {
            mStatus = status;
            mInnerStatus = innerStatus;
            mMime = mime;
            mData = data;
        }

        public int getStatus() {
            return mStatus;
        }

        public int getInnerStatus() {
            return mInnerStatus;
        }

        public String getMime() {
            return mMime;
        }

        public String getData() {
            return mData;
        }
    }

    private static class HttpRequest {
        public static abstract class HttpThreadAction {
            public DataHandlerCallback mCallback = null;

            public HttpThreadAction(DataHandlerCallback callback) {
                mCallback = callback;
            }

            public abstract HttpRequestBase getRequest();
        }

        private HttpThreadAction mAction;

        private Bundle mParameters = null;

        private RetryCallback mRetryCallback;

        private String mRespData = null;

        private String mMimeType = null;

        private String mUserAgent = null;

        private int mInnerStatusCode = 0;

        private int mStatusCode = 0;

        private int mRetryLimit = 5;

        private int mTimeout = 60;

        private int mRedirectLimit = 20;

        private int mRetryCount = 0;

        private boolean mReturnRedirect = false;

        private int mRedirectCount = 0;

        private boolean mIsCanceled = false;

        private URI mRedirectUrl = null;

        private static final Object cancelSync = new Object();

        /**
         * Creates a HttpRequest. Can use both post and get depending on HttpThreadAction.
         *
         * @param context
         * @param action, internal class to describe post or get action.
         * @param parameters, HTTP parameters, such as headers request limits.
         * @param retryCallback interface to notify caller that request is executed once more
         */
        public HttpRequest(Context context, HttpThreadAction action,
                Bundle parameters, RetryCallback retryCallback, boolean returnRedirect) {
            super();
            this.mAction = action;
            this.mParameters = parameters;
            this.mRetryCallback = retryCallback;
            this.mReturnRedirect = returnRedirect;
            if (mParameters != null) {
                int value = mParameters.getInt(Constants.DRM_KEYPARAM_RETRY_COUNT, -1);
                mRetryLimit = (value >= 0) ? value : mRetryLimit;
                value = mParameters.getInt(Constants.DRM_KEYPARAM_TIME_OUT, -1);
                mTimeout = (value > 0) ? value : mTimeout;
                value = mParameters.getInt(Constants.DRM_KEYPARAM_REDIRECT_LIMIT, -1);
                mRedirectLimit = (value >= 0) ? value : mRedirectLimit;
                mUserAgent = mParameters.getString(Constants.DRM_KEYPARAM_USER_AGENT);
            }
            mUserAgent = (mUserAgent == null) ? getDefaultUserAgent(context) : mUserAgent;
        }

        /**
         * Trigger execution of request
         *
         * @return response
         */
        public DLSHttpClient.Response execute() {
            DLSHttpClient.Response response = null;
            AndroidHttpClient client = AndroidHttpClient.newInstance(mUserAgent);
            HttpConnectionParams.setConnectionTimeout(client.getParams(), mTimeout * 1000);
            HttpConnectionParams.setSoTimeout(client.getParams(), mTimeout * 1000);
            ConnManagerParams.setTimeout(client.getParams(), mTimeout * 1000);

            HttpRequestBase mRequest = mAction.getRequest();
            addParameters(mParameters, mRequest);

            boolean isFinished = false;
            do {
                HttpResponse httpResponse = null;
                long requestStartTime = System.currentTimeMillis();
                if (mRequest == null) {
                    // Some severe problem in code or out or memory
                    mStatusCode = Constants.HTTP_ERROR_INTERNAL_ERROR;
                    isFinished = true;
                } else {
                    if (mRetryCount > 0 && mRetryCallback != null) {
                        mRetryCallback.retryingUrl(mStatusCode, mInnerStatusCode,
                                mRequest.getURI().toString());
                    }
                    try {
                        if (!SessionManager.getInstance().isCancelled(mIdMap.keyAt(0))) {
                            DrmLog.debug("execute request towards " + mRequest.getURI());
                            httpResponse = client.execute(mRequest);
                            if (httpResponse != null) {
                                isFinished = handleResponse(httpResponse, mRequest);
                            }
                        } else {
                            mIsCanceled = true;
                            DrmLog.debug("session has been cancelled do not execute request");
                        }
                    } catch (IOException e) {
                        // could be host unreachable or bad address, retry
                        DrmLog.logException(e);
                    } catch (Exception e) {
                        DrmLog.logException(e);
                        mRequest.abort();
                        mStatusCode = Constants.HTTP_ERROR_INTERNAL_ERROR;
                        isFinished = true;
                    }
                }

                if (httpResponse == null && !mIsCanceled && !isFinished) {
                    // Some network error occured
                    if (mRetryCount < mRetryLimit) { // this was not the last retry
                        long requestTotalTime = System.currentTimeMillis() - requestStartTime;
                        mStatusCode = Constants.HTTP_ERROR_TOO_MANY_RETRIES;
                        if (requestTotalTime < mTimeout * 1000) {
                            // Just wait until TIME_OUT sec has passed in total
                            // counted from request start time.
                            synchronized (cancelSync) {
                                try {
                                    cancelSync.wait(mTimeout * 1000 - requestTotalTime);
                                } catch (InterruptedException e) {
                                    DrmLog.logException(e);
                                }
                            }
                        }
                    }
                }

                if (!mIsCanceled && !isFinished && mRetryCount == mRetryLimit) {
                    mInnerStatusCode = mStatusCode;
                    mStatusCode = Constants.HTTP_ERROR_TOO_MANY_RETRIES;
                    isFinished = true;
                } else {
                    mRetryCount++;
                }
            } while (!mIsCanceled && !isFinished);
            if (!mIsCanceled) {
                response = new Response(mStatusCode, mInnerStatusCode, mMimeType, mRespData);
                if (mStatusCode == 200 || mStatusCode == 500) {
                    writeDataToFile("resp", mRespData);
                } else if (mRedirectUrl != null) {
                    response.mRedirect = mRedirectUrl.toString();
                }
            }
            client.close();
            return response;
        }

        /**
         * Cancel current request
         */
        public void prepareCancel() {
            mIsCanceled = true;
            synchronized (cancelSync) {
                cancelSync.notify();
            }
        }

        private String getDefaultUserAgent(Context context) {
            String defaultUserAgent = Constants.FALLBACK_USER_AGENT;
            String versionName = null;
            CharSequence appName = null;
            try {
                PackageManager packageManager = context.getPackageManager();
                PackageInfo pInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                versionName = pInfo.versionName;
                ApplicationInfo applicationInfo = pInfo.applicationInfo;
                appName = packageManager.getApplicationLabel(applicationInfo);
            } catch (NameNotFoundException e) {
                DrmLog.logException(e);
            }
            if (versionName != null && appName != null) {
                defaultUserAgent = appName + "/" + versionName + " (" + Build.VERSION.RELEASE + ")";
            }
            return defaultUserAgent;
        }

        private boolean handleResponse(HttpResponse httpResponse, HttpRequestBase request) {
            boolean requestFinished = false;
            mStatusCode = (httpResponse.getStatusLine() != null) ?
                    httpResponse.getStatusLine().getStatusCode() : mStatusCode;
            DrmLog.debug("mStatusCode " + mStatusCode);
            switch (mStatusCode) {
                case 301:
                case 302:
                case 303:
                case 307:
                    if (httpResponse.getFirstHeader("Location") != null) {
                        if (!mReturnRedirect) {
                            request.setURI(URI.create(httpResponse.getFirstHeader("Location")
                                    .getValue()));
                            // Get requests are made from WebinitiatorManager
                            mRedirectCount++;
                            if (mRedirectCount > mRedirectLimit) {
                                mInnerStatusCode = mStatusCode;
                                mStatusCode = Constants.HTTP_ERROR_TOO_MANY_REDIRECTS;
                                requestFinished = true;
                            }
                            mRetryCount--;
                            // Redirects should not be treated as an retry
                        } else {
                            // Post request made from RequestManager which
                            // result in redirect needs to return and
                            // switch LA_URL and generate a new challenge,
                            // otherwise we just end up with http error 400
                            // once redirected to correct location.
                            mRedirectUrl = URI.create(httpResponse.getFirstHeader("Location")
                                    .getValue());
                            requestFinished = true;
                        }
                    }
                    break;
                case 503:
                    if (!mIsCanceled) {
                        if (mRetryCount < mRetryLimit) {
                            synchronized (cancelSync) {
                                try {
                                    cancelSync.wait(mTimeout * 1000);
                                } catch (InterruptedException e) {
                                    DrmLog.logException(e);
                                }
                            }
                        } else {
                            mInnerStatusCode = mStatusCode; // 503
                        }
                    }
                    break;
                case 408:
                    // just retry
                    break;
                default:
                    // Other status codes should be returned.
                    requestFinished = true;
            }
            if (handleData(httpResponse)) {
                request.abort();
                requestFinished = true; // in case of internal error
            }
            return requestFinished;
        }

        private boolean handleData(HttpResponse httpResponse) {
            DrmLog.debug("handleData");
            boolean abort = false;
            try {
                HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    mMimeType = (entity.getContentType() != null) ? entity.getContentType()
                            .getValue() : mMimeType;
                    InputStream is = entity.getContent();
                    if (mAction.mCallback != null) {
                        mAction.mCallback.handleData(is);
                        // data is handled, abort request
                        abort = true;
                    } else {
                        Header header = httpResponse.getFirstHeader("Content-Type");
                        if (header != null && header.getValue().toLowerCase(Locale.US).
                                contains("charset=utf-16")) {
                            mRespData = inputStreamToBuffer(is, "UTF-16");
                        } else {
                            mRespData = inputStreamToBuffer(is, "UTF-8");
                        }
                        try {
                            is.close();
                        } catch (IOException e) {
                            DrmLog.logException(e);
                        }
                    }
                }
            } catch (Exception e) {
                abort = true;
                mStatusCode = Constants.HTTP_ERROR_INTERNAL_ERROR;
                DrmLog.logException(e);
            }
            return abort;
        }

        private String inputStreamToBuffer(InputStream is, String charset) {
            String result = null;

            try {
                InputStreamReader isr = new InputStreamReader(is, charset);
                int len;
                char[] buf = new char[1024];
                StringBuffer stringBuffer = new StringBuffer();
                while ((len = isr.read(buf, 0, 1024)) != -1) {
                    stringBuffer.append(new String(buf, 0 ,len));
                }
                // response might start with bytes Byte Order Mark which will cause parsing error
                // make sure response starts with '<' in case of XML
                if (stringBuffer.length() > 0) {
                    result = stringBuffer.toString().trim();
                    int xmlStart = result.indexOf('<');
                    if (xmlStart >= 1 && xmlStart < 4) {
                        result = result.substring(xmlStart, result.length());
                    }
                }
            } catch (IOException e) {
                DrmLog.logException(e);
            }
            return result;
        }
    }

    public static void prepareCancel(long sessionId) {
        synchronized (mIdMap) {
            HttpRequest httpRequest = mIdMap.get(sessionId);
            if (httpRequest != null) {
                httpRequest.prepareCancel();
            }
        }
    }

    private static Response executeRequest(Context context, boolean returnRedirect, long sessionId,
            Bundle parameters, RetryCallback retryCallback, HttpRequest.HttpThreadAction action) {
        HttpRequest httpRequest = new HttpRequest(context, action, parameters, retryCallback,
                returnRedirect);
        synchronized (mIdMap) {
            mIdMap.put(sessionId, httpRequest);
        }
        Response response = httpRequest.execute();
        synchronized (mIdMap) {
            mIdMap.remove(sessionId);
        }
        return response;
    }

    public static Response post(Context context, long sessionId, String url, String messageType,
            String data, RetryCallback retryCallback) {
        return post(context, sessionId, url, messageType, data, null, retryCallback, false);
    }

    public static Response post(Context context, long sessionId, String url, String messageType,
            String data, Bundle parameters, RetryCallback retryCallback) {
        return post(context, sessionId, url, messageType, data, parameters, retryCallback, false);
    }

    public static Response post(Context context, long sessionId, String url, String messageType,
            String data, RetryCallback retryCallback, boolean returnRedirect) {
        return post(context, sessionId, url, messageType, data, SessionManager.getInstance()
                .getHttpParams(sessionId), retryCallback, returnRedirect);
    }

    public static Response post(Context context, long sessionId, String url, String messageType,
            String data, Bundle parameters, RetryCallback retryCallback, boolean returnRedirect) {
        final String fUrl = url;
        final String fMessageType = messageType;
        final String fData = data;
        return executeRequest(context, returnRedirect, sessionId, parameters, retryCallback,
                new HttpRequest.HttpThreadAction(null) {
                    public HttpRequestBase getRequest() {
                        HttpPost request = null;
                        if (fUrl != null && fUrl.length() > 0) {
                            try {
                                request = new HttpPost(fUrl);
                                request.setHeader("Content-Type", "text/xml; charset=utf-8");
                                if (fMessageType != null && fMessageType.length() > 0) {
                                    request.setHeader("SOAPAction",
                                            "\"http://schemas.microsoft.com/DRM/2007/03/protocols/"
                                                    + fMessageType + "\"");
                                }
                                if (fData != null && fData.length() > 0) {
                                    writeDataToFile("post", fData);
                                    request.setEntity(new StringEntity(fData));
                                }
                            } catch (UnsupportedEncodingException e) {
                                DrmLog.logException(e);
                            } catch (IllegalArgumentException e) {
                                DrmLog.logException(e);
                            }
                        }
                        return request;
                    }
                });
    }

    public static Response get(Context context, long sessionId, String url,
            RetryCallback retryCallback) {
        return get(context, sessionId, url, SessionManager.getInstance().getHttpParams(sessionId),
                null, retryCallback);
    }

    public static Response get(Context context, long sessionId, String url,
            DataHandlerCallback callback, RetryCallback retryCallback) {
        return get(context, sessionId, url, SessionManager.getInstance().getHttpParams(sessionId),
                callback, retryCallback);
    }

    public static Response get(Context context, long sessionId, String url, Bundle parameters,
            DataHandlerCallback callback, RetryCallback retryCallback) {
        final String fUrl = url;
        return executeRequest(context, false, sessionId, parameters, retryCallback,
                new HttpRequest.HttpThreadAction(callback) {
                    public HttpRequestBase getRequest() {
                        HttpGet request = null;
                        if (fUrl != null && fUrl.length() > 0) {
                            try {
                                request = new HttpGet(fUrl);
                            } catch (IllegalArgumentException e) {
                                DrmLog.logException(e);
                            }
                        }
                        return request;
                    }
                });
    }

    private static void addParameters(Bundle parameters, HttpRequestBase request) {
        if (parameters != null && request != null) {
            Bundle headers = parameters.getBundle(Constants.DRM_KEYPARAM_HTTP_HEADERS);
            if (headers != null && headers.size() > 0) {
                for (String headerKey : headers.keySet()) {
                    if (headerKey != null && headerKey.length() > 0) {
                        String headerValue = headers.getString(headerKey);
                        if (headerValue != null && headerValue.length() > 0) {
                            request.setHeader(headerKey, headerValue);
                        }
                    }
                }
            }
        }
    }

    private static void writeDataToFile(String suffix, String data) {
        if (Constants.DEBUG && data != null) {
            ByteArrayBuffer bab = new ByteArrayBuffer(data.length());
            try {
                bab.append(data.getBytes("UTF-8"), 0, data.length());
                writeDataToFile(suffix, bab);
            } catch (UnsupportedEncodingException e) {
                DrmLog.logException(e);
            }
        }
    }

    private static void writeDataToFile(String suffix, ByteArrayBuffer data) {
        if (Constants.DEBUG) {
            File dir = new File(Environment.getExternalStorageDirectory()  + "/dls");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            String datestr = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS-", Locale.US)
                    .format(new Date());
            File f = new File(Environment.getExternalStorageDirectory() + "/dls/" + datestr
                    + suffix + ".xml");
            if (data != null) {
                try {
                    FileOutputStream fos = new FileOutputStream(f);
                    try {
                        fos.write(data.buffer(), 0, data.length());
                    } finally {
                        fos.close();
                    }
                } catch (FileNotFoundException e) {
                    DrmLog.logException(e);
                } catch (IOException e) {
                    DrmLog.logException(e);
                }
            } else {
                try {
                    f.createNewFile();
                } catch (IOException e) {
                    DrmLog.logException(e);
                }
            }
        }
    }
}
