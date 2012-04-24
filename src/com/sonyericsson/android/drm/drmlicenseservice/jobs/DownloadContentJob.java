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

package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;
import com.sonyericsson.android.drm.drmlicenseservice.R;
import com.sonyericsson.android.drm.drmlicenseservice.Constants;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.drm.DrmManagerClient;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class DownloadContentJob extends StackableJob {
    private String mContentUrl = null;

    private boolean mStatus = false;

    private Cursor mCursor = null;

    private String mDownloadFilePath = null;

    private ContentObserver mObserver = null;

    private DownloadManager mDwldManager = null;

    private long mDownloadId = -1;

    public DownloadContentJob(String contentUrl) {
        mContentUrl = contentUrl;
    }

    @Override
    public boolean executeNormal() {
        if (mJobManager.getCallbackHandler() != null) {
            // We have someone registered for callbacks, then we assume that
            // they will handle/perform the download.
            mStatus = true;
        } else {
            if (mContentUrl != null && mContentUrl.length() > 0) {
                Uri uri = Uri.parse(mContentUrl);
                Context context = mJobManager.getContext();
                if (Looper.myLooper() == null) {
                    Looper.prepare();
                }
                mObserver = new DownloadObserver(new Handler(Looper.myLooper()));
                mDwldManager = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
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
                        return false;
                    }
                    request.setVisibleInDownloadsUi(true);
                    request.setNotificationVisibility(Request.VISIBILITY_VISIBLE);
                    request.setDescription(uri.getHost());

                    Bundle headers = mJobManager.getBundleParameter(
                            Constants.DRM_KEYPARAM_HTTP_HEADERS);
                    if (headers != null && headers.size() > 0) {
                        for (String headerKey : headers.keySet()) {
                            if (headerKey != null && headerKey.length() > 0) {
                                String headerValue = headers.getString(headerKey);
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
                        int localUriIndex = mCursor
                                .getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        mDownloadFilePath = mCursor.getString(localUriIndex);
                        Looper.loop();
                    }
                }
            }
        }
        return mStatus;
    }

    private String getUniqueFileName(File directory, String inputFilename) {
        String filename = null;
        boolean dirCreated = true;
        if (!directory.exists()) {
            dirCreated = directory.mkdirs();
        }
        if (dirCreated) {
            if (!new File(directory, inputFilename).exists()) {
                filename = inputFilename;
            } else {
                String name = inputFilename;
                String ext = "";
                int pointIndex = inputFilename.lastIndexOf(".");
                if (pointIndex != -1) {
                    name = inputFilename.substring(0, pointIndex);
                    ext = inputFilename.substring(pointIndex, inputFilename.length());
                }

                long count = 0;
                do {
                    count--;
                    filename = name + count + ext;
                } while (new File(directory, filename).exists());
            }
            try {
                filename = URLEncoder.encode(filename, "UTF-8");
            } catch (UnsupportedEncodingException e) {
            }
        }
        return filename;
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
            }
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(mDownloadId);
            mCursor = mDwldManager.query(query);
            if (mCursor != null) {
                mCursor.registerContentObserver(mObserver);
                if (mCursor.moveToFirst()) {
                    int statusIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int statusCode = mCursor.getInt(statusIndex);

                    if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
                        endStatus = "SUCCESS";
                        Context context = mJobManager.getContext();

                        // Get filename of downloaded content
                        int titleIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                        String title = mCursor.getString(titleIndex);

                        // Get mimetype of downloaded content
                        int uriIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String path = Uri.decode(mCursor.getString(uriIndex));
                        path = path.replace("file://", "");
                        mDownloadFilePath = path;
                        DrmManagerClient drm = new DrmManagerClient(context);
                        String mimeType = drm.getOriginalMimeType(path);

                        // Create an Intent that will be issued when user taps a
                        // notification of download result .
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(new File(path)), mimeType);
                        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent,
                                0);

                        // Create and Show notification
                        Resources res = context.getResources();
                        String sentence = res.getString(R.string.status_successful_download);
                        Notification notification = new Notification.Builder(context).
                                setSmallIcon(android.R.drawable.stat_sys_download_done).
                                setWhen(System.currentTimeMillis()).
                                setContentTitle(title).
                                setContentText(sentence).
                                setFullScreenIntent(contentIntent, false).
                                getNotification();
                        notification.flags = Notification.FLAG_AUTO_CANCEL;
                        NotificationManager nManager = (NotificationManager)context
                                .getSystemService(Context.NOTIFICATION_SERVICE);
                        nManager.notify((int)mDownloadId, notification);
                    } else if (statusCode == DownloadManager.STATUS_FAILED) {
                        int reasonIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = mCursor.getInt(reasonIndex);
                        endStatus = getStatusCodeForFailedDownload(reason);

                        Context context = mJobManager.getContext();

                        // Get filename of downloaded content
                        int titleIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                        String title = mCursor.getString(titleIndex);
                        if (title == null || title.equals("")) {
                            int uriIndex = mCursor.getColumnIndex(DownloadManager.COLUMN_URI);
                            title = mCursor.getString(uriIndex);
                        }

                        // Create an Intent that will be issued when user
                        // taps a notification of download result.
                        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent,
                                0);

                        // Create and Show notification
                        Resources res = context.getResources();
                        String sentence = res.getString(R.string.status_failed_download);
                        Notification notification = new Notification.Builder(context).
                                setSmallIcon(android.R.drawable.stat_sys_download_done).
                                setWhen(System.currentTimeMillis()).
                                setContentTitle(title).
                                setContentText(sentence).
                                setFullScreenIntent(contentIntent, false).
                                getNotification();
                        NotificationManager nManager = (NotificationManager)context
                                .getSystemService(Context.NOTIFICATION_SERVICE);
                        nManager.notify((int)mDownloadId, notification);
                    }
                } else {
                    // if user has removed download from list the cursor will be
                    // null
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
            Uri filePathUri = Uri.parse(filePath);
            MediaScannerConnection.scanFile(mJobManager.getContext(), new String[] {
                filePathUri.getEncodedPath()
            }, null, null);
            mStatus = true;
        }

        mDwldManager = null;

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    @Override
    public boolean writeToDB(DrmJobDatabase jobDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_TYPE,
                DatabaseConstants.JOBTYPE_DOWNLOAD_CONTENT);
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GENERAL1, this.mContentUrl);
        long result = jobDb.insert(values);
        if (result != -1) {
            super.setDatabaseId(result);
        } else {
            status = false;
        }
        return status;
    }

    @Override
    public boolean readFromDB(Cursor c) {
        this.mContentUrl = c.getString(DatabaseConstants.COLUMN_DWLD_URL);
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_TASKS_POS_GRP_ID));
        return true;
    }
}
