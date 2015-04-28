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
 * Portions created by Sony Mobile Communications Inc. are Copyright (C) 2015
 * Sony Mobile Communications Inc. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UrlConnectionClient {

    /**
     * Keep track of current request.
     * Size 2, since service can have one request from
     * RequestManager and one from WebInitiatorManager.
     */
    private static final LongSparseArray<Request> mIdMap = new LongSparseArray<Request>(2);

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

    /**
     * POST data towards url, and handle reponse.
     * In case returnOnRedirect redirect responses function will return instead of
     * performing redirect.
     *
     * @param context
     * @param sessionId         0 for non AIDL sessions.
     * @param url               target.
     * @param messageType       e.g. AcquireLicense/JoinDomain..
     * @param data              request data.
     * @param parameters        use null to fetch parameters from AIDL session.
     * @param retryCallback     NOT null to receive callback on retry.
     * @param returnOnRedirect  true if redirects are NOT to be performed.
     * @return
     */
    public static Response post(final Context context, long sessionId, final String url,
            final String messageType, final String data, Bundle parameters,
            RetryCallback retryCallback, boolean returnOnRedirect) {

        final Bundle fParameters = (parameters != null) ? parameters:
            SessionManager.getInstance().getHttpParams(sessionId);

        return executeRequest(sessionId, new Request(context, returnOnRedirect, fParameters,
                retryCallback, new Request.RequestAction(null) {

            HttpURLConnection urlConnection = null;

            @Override
            public HttpURLConnection getRequest(String redirectUrl) throws Exception {
                URL targetUrl = URI.create((redirectUrl != null) ? redirectUrl : url).toURL();
                DrmLog.debug("POST Request " + targetUrl);
                urlConnection = (HttpURLConnection)targetUrl.openConnection();
                setParameters(context, urlConnection, fParameters);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                if (messageType != null && messageType.length() > 0) {
                    urlConnection.setRequestProperty("SOAPAction",
                            "\"http://schemas.microsoft.com/DRM/2007/03/protocols/" +
                                    messageType + "\"");
                }
                return urlConnection;
            }

            @Override
            public void sendData() throws IOException {
                if (urlConnection != null) {
                    if (data != null && data.length() > 0) {
                        writeDataToFile("post", data);
                        urlConnection.setFixedLengthStreamingMode(data.length());
                        OutputStream out = urlConnection.getOutputStream();
                        out.write(data.getBytes());
                        out.close();
                    }
                }
            }
        }));
    }

    /**
     * GET call towards url.
     * In case dataCallback response-data will not be returned in Response,
     * but to callback.
     *
     * @param context
     * @param sessionId     0 for non AIDL sessions.
     * @param url           target.
     * @param parameters    null to use parameters for session.
     * @param dataCallback  NOT null to receive data in callback instead of in response.
     * @param retryCallback NOT null to receive callback on retry.
     * @return
     */
    public static Response get(final Context context, long sessionId, final String url,
            Bundle parameters, DataHandlerCallback dataCallback, RetryCallback retryCallback) {

        final Bundle fParameters = (parameters != null) ? parameters:
            SessionManager.getInstance().getHttpParams(sessionId);

        return executeRequest(sessionId, new Request(context, false, fParameters, retryCallback,
                new Request.RequestAction(dataCallback) {

            @Override
            public HttpURLConnection getRequest(String redirectUrl) throws Exception {
                HttpURLConnection urlConnection = null;
                URL targetUrl = URI.create((redirectUrl != null) ? redirectUrl : url).toURL();
                DrmLog.debug("GET Request " + targetUrl);
                urlConnection = (HttpURLConnection)targetUrl.openConnection();
                setParameters(context, urlConnection, fParameters);
                return urlConnection;
            }
        }));
    }

    private static String getDefaultUserAgent(Context context) {
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

    private static void setParameters(Context context, HttpURLConnection con, Bundle parameters) {
        if (con != null) {
            con.setInstanceFollowRedirects(false);
            int timeout = 60;
            String userAgent = getDefaultUserAgent(context);
            if (parameters != null) {
                timeout = (parameters.getInt(Constants.DRM_KEYPARAM_TIME_OUT, -1) != -1) ?
                        parameters.getInt(Constants.DRM_KEYPARAM_TIME_OUT) : timeout;
                userAgent = (parameters.getString(Constants.DRM_KEYPARAM_USER_AGENT) != null) ?
                        parameters.getString(Constants.DRM_KEYPARAM_USER_AGENT) : userAgent;
                Bundle headers = parameters.getBundle(Constants.DRM_KEYPARAM_HTTP_HEADERS);
                if (headers != null && headers.size() > 0) {
                    for (String headerKey : headers.keySet()) {
                        if (headerKey != null && headerKey.length() > 0) {
                            String headerValue = headers.getString(headerKey);
                            if (headerValue != null && headerValue.length() > 0) {
                                con.setRequestProperty(headerKey, headerValue);
                            }
                        }
                    }
                }
            }
            con.setRequestProperty("User-Agent", userAgent);
            con.setConnectTimeout(timeout * 1000);
            con.setReadTimeout(timeout * 1000);
        }
    }

    /*
     * Function to keep track of current executing requests
     */
    private static Response executeRequest(long sessionId, Request request) {
        synchronized (mIdMap) {
            mIdMap.put(sessionId, request);
        }
        Response response = request.execute();
        synchronized (mIdMap) {
            mIdMap.remove(sessionId);
        }
        return response;
    }

    private static class Request {

        private static abstract class RequestAction {

            private DataHandlerCallback mCallback = null;

            /*
             * RequestAction, used to define GET or POST requests e.g.
             */
            private RequestAction(DataHandlerCallback callback) {
                mCallback = callback;
            }

            /*
             * Performs sending action, can be used by POST action e.g.
             */
            public void sendData() throws IOException {}

            /**
             * Fetch new request for action.
             * @param redirectUrl optional, new url for redirect
             */
            public abstract HttpURLConnection getRequest(String redirectUrl) throws Exception;
        }

        private RequestAction mAction;

        private RetryCallback mRetryCallback;

        private boolean mReturnRedirect, mIsCanceled = false;

        private String mRedirectUrl = null;

        private String mRespData, mMimeType;

        private static final Object cancelSync = new Object();

        private int mRetryLimit = 5,
                mRetryCount = 0,
                mRedirectLimit = 20,
                mRedirectCount = 0,
                mStatusCode = 0,
                mInnerStatusCode = 0,
                mTimeout = 60;

        public Request(Context context, boolean returnRedirect,
                Bundle parameters, RetryCallback retryCallback,
                RequestAction requestAction) {
            mAction = requestAction;
            mRetryCallback = retryCallback;
            mReturnRedirect = returnRedirect;

            if (parameters != null) {
                DrmLog.debug(parameters.toString());
                int value = parameters.getInt(Constants.DRM_KEYPARAM_RETRY_COUNT, -1);
                mRetryLimit = (value >= 0) ? value : mRetryLimit;
                value = parameters.getInt(Constants.DRM_KEYPARAM_TIME_OUT, -1);
                mTimeout = (value > 0) ? value : mTimeout;
                value = parameters.getInt(Constants.DRM_KEYPARAM_REDIRECT_LIMIT, -1);
                mRedirectLimit = (value >= 0) ? value : mRedirectLimit;
            }
        }

        public Response execute() {
            Response response = null;
            boolean isFinished = false;

            do {
                long requestStartTime = System.currentTimeMillis();
                try {
                    HttpURLConnection urlConnection = mAction.getRequest(mRedirectUrl);
                    if (mRetryCount > 0 && mRetryCallback != null) {
                        mRetryCallback.retryingUrl(mStatusCode, mInnerStatusCode,
                                urlConnection.getURL().toString());
                    }
                    try {
                        if (!SessionManager.getInstance().isCancelled(mIdMap.keyAt(0))) {
                            DrmLog.debug("execute request towards " + urlConnection.getURL());
                            mAction.sendData();
                            isFinished = handleResponse(urlConnection);
                        } else {
                            mIsCanceled = true;
                            DrmLog.debug("Session has been cancelled, will not execute request");
                        }
                        urlConnection.disconnect();
                    } catch (SocketTimeoutException|UnknownHostException|ConnectException e) {
                        DrmLog.logException(e);
                        if (mRetryCount < mRetryLimit) {
                            // this was not the last retry
                            long requestTotalTime = System.currentTimeMillis() - requestStartTime;
                            mStatusCode = Constants.HTTP_ERROR_TOO_MANY_RETRIES;
                            if (requestTotalTime < mTimeout * 1000) {
                                // Just wait until TIME_OUT sec has passed in total
                                // counted from request start time.
                                synchronized (cancelSync) {
                                    try {
                                        cancelSync.wait(mTimeout * 1000 - requestTotalTime);
                                    } catch (InterruptedException ie) {
                                        DrmLog.logException(e);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    DrmLog.logException(e);
                    mStatusCode = Constants.HTTP_ERROR_INTERNAL_ERROR;
                    isFinished = true;
                }
                if (!mIsCanceled && !isFinished && mRetryCount == mRetryLimit) {
                    mInnerStatusCode = mStatusCode;
                    mStatusCode = Constants.HTTP_ERROR_TOO_MANY_RETRIES;
                    isFinished = true;
                } else {
                    mRetryCount++;
                }
            } while (!isFinished && !mIsCanceled);
            if (!mIsCanceled) {
                response = new Response(mStatusCode, mInnerStatusCode, mMimeType, mRespData);
                if (mStatusCode == 200 || mStatusCode == 500) {
                    writeDataToFile("resp", mRespData);
                } else if (mRedirectUrl != null) {
                    response.mRedirect = mRedirectUrl.toString();
                }
            }
            return response;
        }

        private boolean handleResponse(HttpURLConnection con) throws IOException {
            boolean requestFinished = false;
            mStatusCode = con.getResponseCode();
            DrmLog.debug("mStatusCode " + mStatusCode);
            switch (mStatusCode) {
                case 301:
                case 302:
                case 303:
                case 307:
                    if (con.getHeaderField("Location") != null) {
                        mRedirectUrl = con.getHeaderField("Location");
                        if (!mReturnRedirect) {
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
            return handleData(con) || requestFinished;
        }

        private boolean handleData(HttpURLConnection con) throws IOException {
            boolean abort = false;
            mMimeType = con.getContentType();
            InputStream is = null;
            try {
                is = con.getInputStream();
            } catch (FileNotFoundException e) {
                is = con.getErrorStream();
            }
            if (mAction.mCallback != null) {
                mAction.mCallback.handleData(is);
                // data is handled, abort request
                abort = true;
            } else {
                String contentType = con.getContentType();
                if (contentType != null &&
                        contentType.toLowerCase(Locale.US).contains("charset=utf-16")) {
                    mRespData = inputStreamToString(is, "UTF-16");
                } else {
                    mRespData = inputStreamToString(is, "UTF-8");
                }
                try {
                    is.close();
                } catch (IOException e) {
                    DrmLog.logException(e);
                }
            }
            return abort;
        }

        private String inputStreamToString(InputStream is, String charset) {
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

        public void cancel() {
            mIsCanceled = true;
            synchronized (cancelSync) {
                cancelSync.notify();
            }
        }

    }

    public static class Response {
        private int mStatus = 0;

        private int mInnerStatus = 0;

        private String mMime = null;

        private String mData = null;

        public String mRedirect = null;

        public Response(int status, int innerStatus, String mime, String data) {
            DrmLog.debug("new Reponse status :" + status + " inner status: " + innerStatus);
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

    public static void prepareCancel(long sessionId) {
        synchronized (mIdMap) {
            Request request = mIdMap.get(sessionId);
            if (request != null) {
                request.cancel();
            }
        }
    }

    /*
     * Debug function to write HTTP traffic to file
     */
    private static void writeDataToFile(String suffix, String data) {
        if (Constants.DEBUG) {
            File dir = new File(Environment.getExternalStorageDirectory()  + "/dls");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            String datestr = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS-", Locale.US)
                    .format(new Date());
            File f = new File(Environment.getExternalStorageDirectory() + "/dls/" + datestr
                    + suffix + ".xml");
            try {
                if (data != null) {
                    FileOutputStream fos = new FileOutputStream(f);
                    try {
                        fos.write(data.getBytes());
                    } finally {
                        fos.close();
                    }
                } else {
                    f.createNewFile();
                }
            } catch (IOException e) {
                DrmLog.logException(e);
            }
        }
    }
}
