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
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.util.ByteArrayBuffer;

import android.net.http.AndroidHttpClient;
import android.os.Bundle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HttpClient {

    private static HashMap<Long, HttpThread> mIdMap = new HashMap<Long, HttpClient.HttpThread>(1);

    // Optional interface to implement by clients, if they need data during
    // download
    public interface DataHandlerCallback {
        // Perform optional data handling in client during download.
        // Return value is used to indicate if download should continue, false
        // -> cancel
        public boolean handleData(byte[] buffer, int length);
    }

    public static class Response {
        private int mStatus = 0;

        private int mInnerStatus = 0;

        private String mMime = null;

        private ByteArrayBuffer mData = null;

        public Response(int status, int innerStatus, String mime, ByteArrayBuffer data) {
            mStatus = status;
            mInnerStatus = innerStatus;
            if (mime != null && mime.length() > 0) {
                mMime = mime;
            }
            if (data != null && data.length() > 0) {
                mData = data;
            }
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
            if (mData == null) {
                return null;
            } else {
                return new String(mData.buffer(), 0, mData.length());
            }
        }

        public ByteArrayBuffer getByteData() {
            return mData;
        }

        @Override
        public String toString() {
            return "Response [" + mStatus + "(" + mInnerStatus + ") " + mMime + " " + mData + "]";
        }
    }

    private static class HttpThread extends Thread {
        public static abstract class HttpThreadAction {
            public DataHandlerCallback mCallback = null;

            public HttpThreadAction(DataHandlerCallback callback) {
                mCallback = callback;
            }

            public abstract HttpRequestBase getRequest();
        }

        public CountDownLatch latch = new CountDownLatch(1);

        public HttpClient.Response response = null;

        private int innerStatusCode = 0;

        private int statusCode = 0;

        private Bundle mParameters;

        private HttpThreadAction mAction;

        private int retry_count = 5;

        private int timeout = 60;

        private int redirect_limit = 20;

        private boolean keepRunning = true;

        public void prepareCancel() {
            keepRunning = false;
        }

        public boolean getKeepRunning() {
            return keepRunning;
        }

        public HttpThread(Bundle parameters, HttpThreadAction action) {
            super();
            this.mParameters = parameters;
            this.mAction = action;
            if (parameters != null) {
                if (parameters.containsKey(Constants.DRM_KEYPARAM_RETRY_COUNT)) {
                    int value = parameters.getInt(Constants.DRM_KEYPARAM_RETRY_COUNT);
                    if (value >= 0) {
                        retry_count = value;
                    }
                }
                if (parameters.containsKey(Constants.DRM_KEYPARAM_TIME_OUT)) {
                    int value = parameters.getInt(Constants.DRM_KEYPARAM_TIME_OUT);
                    if (value > 0) {
                        timeout = value;
                    }
                }
                if (parameters.containsKey(Constants.DRM_KEYPARAM_REDIRECT_LIMIT)) {
                    int value = parameters.getInt(Constants.DRM_KEYPARAM_REDIRECT_LIMIT);
                    if (value >= 0) {
                        redirect_limit = value;
                    }
                }
            }
        }

        public void run() {
            String userAgent = getUserAgent(mParameters);
            AndroidHttpClient client = AndroidHttpClient.newInstance(userAgent);
            HttpConnectionParams.setConnectionTimeout(client.getParams(), timeout * 1000);
            HttpConnectionParams.setSoTimeout(client.getParams(), timeout * 1000);
            ConnManagerParams.setTimeout(client.getParams(), timeout * 1000);

            ByteArrayBuffer respData = null;
            String mimeType = null;
            int retryNumber = 0;
            int redirectNumber = 0;
            URI newUri = null;

            do {
                HttpResponse httpResponse = null;
                boolean responseReceieved = false;

                if (getKeepRunning() == false) {
                    break;
                }

                if (retryNumber > retry_count) {
                    innerStatusCode = statusCode;
                    statusCode = -1;
                    break;
                }

                HttpRequestBase request = mAction.getRequest();
                Exception requestException = null;
                long requestStartTime = System.currentTimeMillis();
                if (request == null) {
                    // Some severe problem in code or out or memory
                    statusCode = -4;
                    break;
                } else {
                    if (newUri != null) {
                        request.setURI(newUri);
                    }
                    try {
                        httpResponse = client.execute(request);
                        if (httpResponse != null) {
                            try {
                                statusCode = httpResponse.getStatusLine().getStatusCode();
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            }
                            try {
                                mimeType = httpResponse.getEntity().getContentType().getValue();
                            } catch (NullPointerException e) {
                            }
                            try {
                                respData = inputStreamToBuffer(request, httpResponse.getEntity()
                                        .getContent());
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            }
                            responseReceieved = true;
                        }
                    } catch (IllegalStateException e) {
                        request.abort();
                        statusCode = -4;
                        break;
                    } catch (IOException e) {
                        request.abort();
                        requestException = e;
                    }
                }
                retryNumber++;
                if (!responseReceieved) {
                    if (getKeepRunning() == false) {
                        break;
                    }
                    // Some network error occured
                    if (retryNumber <= retry_count && requestException != null) {
                        long requestTotalTime = System.currentTimeMillis() - requestStartTime;
                        statusCode = -1;
                        if (requestTotalTime < timeout * 1000) {
                            // Just wait until TIME_OUT sec has passed in total
                            // counted from request start time.
                            try {
                                Thread.sleep(timeout * 1000 - requestTotalTime);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                } else {
                    // Some response received, check status to handle some
                    // of them
                    if (statusCode == 200 || statusCode == 500) {
                        // Valid
                        break;
                    } else if (statusCode == 301 || statusCode == 302 || statusCode == 303
                            || statusCode == 307) {
                        if (httpResponse == null) {
                            // Try it again since something is wrong
                            continue;
                        }
                        if (httpResponse.getFirstHeader("Location") == null) {
                            // Try it again since something is wrong
                            continue;
                        }
                        // Handle redirects
                        redirectNumber++;
                        String newUriString = httpResponse.getFirstHeader("Location").getValue();
                        if (redirectNumber > redirect_limit) {
                            innerStatusCode = statusCode;
                            statusCode = -3;
                            break;
                        }
                        newUri = URI.create(newUriString);
                        retryNumber--; // Redirects should not be treated as
                                       // an retry
                    } else if (statusCode == 503) { // Service Unavailable
                        if (getKeepRunning() == false) {
                            break;
                        }
                        if (retryNumber <= retry_count) {
                            try {
                                Thread.sleep(timeout * 1000);
                            } catch (InterruptedException e) {
                            }
                        } else {
                            innerStatusCode = statusCode; // 503
                        }
                    } else if (statusCode == 408) {
                        // Just retry
                    } else {
                        // Other status codes should be returned.
                        break;
                    }
                }
            } while (true);
            if (getKeepRunning() == true) {
                response = new Response(statusCode, innerStatusCode, mimeType, respData);
                if (statusCode == 200 || statusCode == 500) {
                    writeDataToFile("resp", respData);
                }
            }
            client.close();

            latch.countDown();
        }

        public int getLastStatusCode() {
            if (innerStatusCode != 0) {
                return innerStatusCode;
            } else {
                return statusCode;
            }
        }

        private ByteArrayBuffer inputStreamToBuffer(HttpRequestBase request, InputStream is) {
            BufferedInputStream bis = new BufferedInputStream(is);
            ByteArrayBuffer bab = new ByteArrayBuffer(4096);
            try {
                int len = -1;
                byte[] buf = new byte[1024];
                while ((len = bis.read(buf, 0, 1024)) != -1) {
                    bab.append(buf, 0, len);
                    if (mAction.mCallback != null) {
                        if (!mAction.mCallback.handleData(buf, len)) {
                            // Callback was unsuccessful. This indicates that
                            // download shall be stopped.
                            request.abort();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                bab = null;
            }

            try {
                is.close();
            } catch (IOException e) {
            }
            try {
                bis.close();
            } catch (IOException e) {
            }

            return bab;
        }
    }

    public static void prepareCancel(long sessionId) {
        synchronized (mIdMap) {
            HttpThread t = mIdMap.get(sessionId);
            if (t != null) {
                t.prepareCancel();
                if (t.getState() == Thread.State.TIMED_WAITING) {
                    // Stop thread from sleeping
                    t.interrupt();
                }
            }
        }
    }

    private static Response runAsThread(long sessionId, Bundle parameters,
            HttpThread.HttpThreadAction action) {
        int retryCount = 5;
        int timeOut = 60;
        if (parameters != null) {
            if (parameters.containsKey(Constants.DRM_KEYPARAM_RETRY_COUNT)) {
                int value = parameters.getInt(Constants.DRM_KEYPARAM_RETRY_COUNT);
                if (value >= 0) {
                    retryCount = value;
                }
            }
            if (parameters.containsKey(Constants.DRM_KEYPARAM_TIME_OUT)) {
                int value = parameters.getInt(Constants.DRM_KEYPARAM_TIME_OUT);
                if (value > 0) {
                    timeOut = value;
                }
            }
        }
        HttpThread t = new HttpThread(parameters, action);

        synchronized (mIdMap) {
            mIdMap.put(sessionId, t);
        }

        t.start();
        try {
            t.latch.await((retryCount + 1) * (timeOut + 1), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (t.response != null) {
        } else if (t.getKeepRunning() == false) {
        } else {
            t.response = new Response(-2, t.getLastStatusCode(), null, null);
        }
        synchronized (mIdMap) {
            mIdMap.remove(sessionId);
        }
        return t.response;
    }

    private HttpClient() {
    }

    public static Response post(long sessionId, String url, String messageType, String data) {
        return post(sessionId, url, messageType, data, null, null);
    }

    public static Response post(long sessionId, String url, String messageType, String data,
            Bundle parameters) {
        return post(sessionId, url, messageType, data, parameters, null);
    }

    public static Response post(long sessionId, String url, String messageType, String data,
            Bundle parameters, DataHandlerCallback callback) {
        Response response = null;
        final String fUrl = url;
        final String fMessageType = messageType;
        final String fData = data;
        final Bundle fParameters = parameters;
        response = runAsThread(sessionId, parameters, new HttpThread.HttpThreadAction(callback) {
            public HttpRequestBase getRequest() {
                HttpPost request = null;
                if (fUrl != null && fUrl.length() > 0) {
                    try {
                        request = new HttpPost(fUrl);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    request.setHeader("Content-Type", "text/xml; charset=utf-8");
                    addParameters(fParameters, request);
                    if (fMessageType != null && fMessageType.length() > 0) {
                        request.setHeader("SOAPAction",
                                "\"http://schemas.microsoft.com/DRM/2007/03/protocols/"
                                        + fMessageType + "\"");
                    }
                    if (fData != null && fData.length() > 0) {
                        ByteArrayBuffer bab = new ByteArrayBuffer(fData.length());
                        bab.append(fData.getBytes(), 0, fData.length());
                        writeDataToFile("post", bab);
                        try {
                            request.setEntity(new StringEntity(fData));
                        } catch (UnsupportedEncodingException e) {
                        }
                    }
                }
                return request;
            }
        });
        return response;
    }

    public static Response get(long sessionId, String url) {
        return get(sessionId, url, null, null);
    }

    public static Response get(long sessionId, String url, Bundle parameters) {
        return get(sessionId, url, parameters, null);
    }

    public static Response get(long sessionId, String url, Bundle parameters,
            DataHandlerCallback callback) {
        Response response = null;
        final String fUrl = url;
        final Bundle fParameters = parameters;
        response = runAsThread(sessionId, parameters, new HttpThread.HttpThreadAction(callback) {
            public HttpRequestBase getRequest() {
                HttpGet request = null;
                if (fUrl != null && fUrl.length() > 0) {
                    try {
                        request = new HttpGet(fUrl);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }

                    addParameters(fParameters, request);
                }
                return request;
            }
        });
        return response;
    }

    private static void addParameters(Bundle parameters, HttpRequestBase request) {
        if (parameters != null) {
            if (parameters.containsKey(Constants.DRM_KEYPARAM_HTTP_HEADERS)) {
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
    }

    private static String getUserAgent(Bundle parameters) {
        String userAgent = "PlayReadyApp";
        if (parameters != null && parameters.containsKey(Constants.DRM_KEYPARAM_USER_AGENT)) {
            String tempUA = parameters.getString(Constants.DRM_KEYPARAM_USER_AGENT);
            if (tempUA != null && tempUA.length() > 0) {
                userAgent = tempUA;
            }
        }
        return userAgent;
    }

    private static void writeDataToFile(String suffix, ByteArrayBuffer data) {
        if (Constants.DEBUG) {
            File d = new File("/sdcard/dls");
            /* findbugs warning. Should perhaps be getexternalstorage? */
            if (!d.exists()) {
                d.mkdirs();
                /* findbugs wants the return to be checked */
            }
            String datestr = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS-").format(new Date());
            File f = new File("/sdcard/dls/" + datestr + suffix + ".xml");
            if (data != null) {
                try {
                    FileOutputStream fos = new FileOutputStream(f);
                    try {
                        fos.write(data.buffer(), 0, data.length());
                    } finally {
                        fos.close();
                    }
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                }
            } else {
                try {
                    f.createNewFile();
                } catch (IOException e) {
                }
            }
        }
    }
}
