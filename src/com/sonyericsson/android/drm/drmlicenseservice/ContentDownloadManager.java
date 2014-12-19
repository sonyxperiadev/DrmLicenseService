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

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Locale;

public class ContentDownloadManager implements Runnable {

    private String mContentUrl = null;

    private Cursor mCursor = null;

    private String mDownloadFilePath = null;

    private ContentObserver mObserver = null;

    private DownloadManager mDwldManager = null;

    private long mDownloadId = -1;

    private Context mContext;

    private Bundle mHeaders;

    /**
     * Creates a ContentDownloadManager, handles downloading of content, and
     * notifications for that download.
     *
     * @param context
     * @param contentUrl target of download
     * @param sessionId dls session id
     */
    public ContentDownloadManager(Context context, String contentUrl, long sessionId) {
        mContext = context;
        mContentUrl = contentUrl;
        Bundle httpParams = SessionManager.getInstance().getHttpParams(sessionId);
        if (httpParams != null) {
            mHeaders = httpParams.getBundle(Constants.DRM_KEYPARAM_HTTP_HEADERS);
        }
    }

    @Override
    public void run() {
        if (mContentUrl != null && mContentUrl.length() > 0) {
            Uri uri = Uri.parse(mContentUrl);
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            mObserver = new DownloadObserver(new Handler(Looper.myLooper()));
            mDwldManager = (DownloadManager)mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            String lastPart = uri.getLastPathSegment();
            String filename = null;
            String directory = Environment.DIRECTORY_DOWNLOADS;
            if (lastPart != null && lastPart.length() > 0 && lastPart.contains(".")) {
                filename = getUniqueFileName(
                        Environment.getExternalStoragePublicDirectory(directory), lastPart);
            }
            if (null != filename) {
                try {
                    request.setDestinationInExternalPublicDir(directory, filename);
                } catch (IllegalStateException e) {
                    DrmLog.logException(e);
                    return;
                }
                request.setVisibleInDownloadsUi(true);
                request.setNotificationVisibility(Request.VISIBILITY_VISIBLE);
                request.setDescription(uri.getHost());

                if (mHeaders != null && mHeaders.size() > 0) {
                    for (String headerKey : mHeaders.keySet()) {
                        if (headerKey != null && headerKey.length() > 0) {
                            String headerValue = mHeaders.getString(headerKey);
                            if (headerValue != null && headerValue.length() > 0) {
                                request.addRequestHeader(headerKey, headerValue);
                            }
                        }
                    }
                }
                mDownloadId = mDwldManager.enqueue(request);
                // set up an observer to get status
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(mDownloadId);
                mCursor = mDwldManager.query(query);
                if (mCursor != null && mCursor.moveToFirst()) {
                    mCursor.registerContentObserver(mObserver);
                    int localUriIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    mDownloadFilePath = mCursor.getString(localUriIndex);
                    Looper.loop();
                }
            }
        }
    }

    private String getUniqueFileName(File directory, String inputFilename) {
        File file = new File(directory, inputFilename);
        if (directory.exists() || directory.mkdirs()) {
            long fileIndex = -1;
            String extension = "";
            String filename = "";
            while (file.exists()) {
                int pointIndex = inputFilename.lastIndexOf('.');
                if (pointIndex > 0) {
                    extension = inputFilename.substring(pointIndex);
                    filename = inputFilename.substring(0, pointIndex);
                }
                file = new File(directory, filename + fileIndex-- + extension);
            }
        }
        return file.getName();
    }

    private class DownloadObserver extends ContentObserver {
        public DownloadObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            String endStatus = null;
            if (mCursor != null) {
                mCursor.unregisterContentObserver(mObserver);
                mCursor.close();
            }
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(mDownloadId);
            mCursor = mDwldManager.query(query);
            if (mCursor != null) {
                mCursor.registerContentObserver(mObserver);
                if (mCursor.moveToFirst()) {
                    int index = mCursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int statusCode = (index != -1) ? mCursor.getInt(index) : -1;
                    if (statusCode == DownloadManager.STATUS_SUCCESSFUL ||
                            statusCode == DownloadManager.STATUS_FAILED) {
                        // Get filename of downloaded content
                        index = mCursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                        String title = (index != -1) ? mCursor.getString(index) : null;

                        // Create an Intent that will be issued when user
                        // taps a notification of download result.
                        Intent intent = null;
                        String sentence = null;
                        Resources res = mContext.getResources();
                        Notification notification;
                        if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
                                endStatus = "SUCCESS";
                                index = mCursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                                String path = (index != -1) ? mCursor.getString(index) : null;
                                String mimeType = null;
                                intent = new Intent(Intent.ACTION_VIEW);
                                if (path != null) {
                                    mDownloadFilePath = Uri.decode(path).replace("file://", "");
                                    if (path.toLowerCase(Locale.US).endsWith(".isma")) {
                                        mimeType = "audio/isma";
                                    } else if (path.toLowerCase(Locale.US).endsWith(".ismv")) {
                                        mimeType = "video/ismv";
                                    }
                                    intent.setDataAndType(Uri.parse(path), mimeType);
                                }
                                sentence = res.getString(R.string.status_successful_download);
                        } else { // DownloadManager.STATUS_FAILED:
                                index = mCursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                endStatus = getStatusCodeForFailedDownload(mCursor.getInt(index));
                                if (title == null || title.equals("")) {
                                    index = mCursor.getColumnIndex(DownloadManager.COLUMN_URI);
                                    title = (index != -1) ? mCursor.getString(index) : title;
                                }
                                intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                                sentence = res.getString(R.string.status_failed_download);
                        }
                        // Create and Show notification
                        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
                                intent, 0);
                        notification = new Notification.Builder(mContext)
                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                .setWhen(System.currentTimeMillis()).setContentTitle(title)
                                .setContentText(sentence).setContentIntent(contentIntent).build();
                        if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
                            notification.flags = Notification.FLAG_AUTO_CANCEL;
                        }
                        NotificationManager nManager = (NotificationManager)mContext
                                .getSystemService(Context.NOTIFICATION_SERVICE);
                        nManager.notify((int)mDownloadId, notification);
                    }
                } else {
                    // if user has removed download from list the cursor will be null
                    endStatus = "USER_CANCELLED";
                }
            }

            // done, clean up and send status
            if (endStatus != null) {
                if (mCursor != null && mObserver != null) {
                    mCursor.unregisterContentObserver(mObserver);
                    mObserver = null;
                }
                Looper.myLooper().quit();
                finishDownload(mDownloadFilePath, endStatus);
            }
        }
    }

    private static String getStatusCodeForFailedDownload(int reason) {
        String endStatus = "error";
        switch (reason) {
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
            case DownloadManager.ERROR_UNKNOWN:
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                endStatus = "DEVICE_ABORTED";
                break;
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
            case DownloadManager.ERROR_FILE_ERROR:
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                endStatus = "INSUFFICIENT_MEMORY";
                break;
            case DownloadManager.ERROR_CANNOT_RESUME:
                endStatus = "LOSS_OF_SERVICE";
                break;
            default:
                break;
        }
        return endStatus;
    }

    /*
     * Send status report and clean up
     */
    private void finishDownload(String filePath, String downloadStatus) {
        if (downloadStatus.equals("SUCCESS") && filePath != null) {
            DrmLog.debug("download passed" + filePath);
            Uri filePathUri = Uri.parse(filePath);
            MediaScannerConnection.scanFile(mContext, new String[] {
                filePathUri.getEncodedPath()
            }, null, null);
        } else {
            DrmLog.debug("download failed");
        }

        mDwldManager = null;

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

}
