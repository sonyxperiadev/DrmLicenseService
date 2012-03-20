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

import com.sonyericsson.android.drm.drmlicenseservice.jobs.AcknowledgeLicenseJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.AcquireLicenseJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.DownloadContentJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.DrmFeedbackJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.GetMeteringCertificateJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.JoinDomainJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.LaunchLuiUrlIfFailureJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.LeaveDomainJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.ProcessMeteringDataJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.RenewRightsJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.StackableJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.WebInitiatorJob;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DrmJobDatabase extends SQLiteOpenHelper {
    private static final String TAG = DrmJobDatabase.class.getSimpleName();

    private static DrmJobDatabase sInstance = null;

    private static final ReentrantLock lock = new ReentrantLock();

    private static SQLiteDatabase sqlDb = null;

    private static final int DB_TRYCOUNT = 10;

    private static final int DB_TRYWAITTIME = 100;

    /**
     * The database that is used to save jobs
     */

    public static synchronized DrmJobDatabase getInstance(Context context) {
        boolean gotResult = false;
        int retryCount = 0;
        do {
            try {
                lock.lock();
                if (sInstance == null) {
                    sInstance = new DrmJobDatabase(context);
                }
                if (sqlDb == null) {
                    sqlDb = sInstance.getWritableDatabase();
                }
                gotResult = true;
            } catch (SQLiteException e) {
                Log.e(TAG, "Could not create database: " + e.getMessage() + " will try again");
            } finally {
                lock.unlock();
            }
            if (!gotResult) {
                try {
                    Thread.sleep(DB_TRYWAITTIME);
                } catch(InterruptedException e) {
                }
            }
        } while ((!gotResult) && DB_TRYCOUNT > (++retryCount));

        if (!gotResult) {
            return null;
        }

        return sInstance;
    }

    private DrmJobDatabase(Context context) {
        super(context, DatabaseConstants.DATABASE_NAME, null, DatabaseConstants.DATABASE_VERSION);
        if (Constants.DEBUG) {
            Log.d(TAG, "Constructor");
        }
    }

    /*
     * Currently implemented to take away the EXACT record (by id) but could be
     * implemented to delete all records containing the same content as the
     * record thus removing duplicates
     */
    public boolean removeTask(long id) {
        boolean result = false;
        try {
            lock.lock();
            String where = "id = ?";
            String args[] = {
                String.valueOf(id)
            };
            int rows = sqlDb.delete(DatabaseConstants.DATABASE_TASKS_TABLE_NAME, where, args);
            SQLiteDatabase.releaseMemory();
            if (Constants.DEBUG) {
                Log.d(TAG, "Remove: Number of rows affected in database when removing id["
                        + args[0] + "]: " + rows);
            }
            result = rows >= 1;
        } catch (SQLiteException e) {
            Log.e(TAG, "Sqlite exception while trying to remove job: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return result;
    }

    public boolean removeParameter(long sessionid, String key) {
        boolean result = false;
        try {
            lock.lock();
            String where = "sessionid = ? AND key = ?";
            String args[] = {
                    String.valueOf(sessionid), String.valueOf(key)
            };
            int rows = sqlDb.delete(DatabaseConstants.DATABASE_PARAMETERS_TABLE_NAME, where, args);
            SQLiteDatabase.releaseMemory();
            if (Constants.DEBUG) {
                Log.d(TAG, "Remove: Number of rows affected in database when removing parameter["
                        + sessionid + "-" + key + "]: " + rows);
            }
            result = rows >= 1;
        } catch (SQLiteException e) {
            Log.e(TAG, "Sqlite exception while trying to remove parameter: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return result;
    }

    public StackableJob getNext() {
        StackableJob result = null;
        Cursor c = null;
        try {
            lock.lock();
            c = sqlDb.query(DatabaseConstants.DATABASE_TASKS_TABLE_NAME, null, null, null, null,
                    null, null);

            if (c != null) {
                /* convert the first one and return that datatype */
                if (c.moveToFirst() && !c.isAfterLast()) {
                    result = converttoStackableJob(c);
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "getNext: We have a exception - will return null " + e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
            lock.unlock();
        }
        return result;

    }

    public int addDatabaseJobsToStack(JobManager jm, Long sessionId, Handler statusHandler) {
        int addedJobs = 0;
        Cursor c = null;
        boolean statusSent = false;
        try {
            lock.lock();
            c = getDatabaseTaskContents();

            if (c != null) {
                c.moveToFirst();
                while (!c.isAfterLast()) {
                    long jobSessionId = c.getLong(DatabaseConstants.COLUMN_TASKS_POS_SESSION_ID);
                    if (jobSessionId == sessionId) {
                        if (!statusSent && statusHandler != null) {
                            Message msg = new Message();
                            msg.obj = Boolean.TRUE;
                            statusHandler.sendMessage(msg);
                            statusSent = true;
                        }

                        StackableJob job = converttoStackableJob(c);
                        if (job != null) {
                            job.setDatabaseId(c.getLong(DatabaseConstants.COLUMN_TASKS_POS_ID));
                            jm.pushJobNoDatabase(job);
                            addedJobs++;
                        }
                    }
                    c.moveToNext();
                }
                if (Constants.DEBUG) {
                    Log.d(Constants.LOGTAG, "Finished checking for unfinished jobs in DB ("
                            + addedJobs + ") total jobs in database: " + c.getCount());
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Got error when trying to read in jobs from DB. Error: " + e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
            lock.unlock();
        }
        if (!statusSent && statusHandler != null) {
            Message msg = new Message();
            msg.obj = Boolean.FALSE;
            statusHandler.sendMessage(msg);
        }
        return addedJobs;
    }

    private StackableJob converttoStackableJob(Cursor cursor) {
        StackableJob result = null;
        int colType = cursor.getInt(DatabaseConstants.COLUMN_TASKS_POS_TYPE);
        switch (colType) {
            case DatabaseConstants.JOBTYPE_ACKNOWLEDGE_LICENSE:
                AcknowledgeLicenseJob ackResult = new AcknowledgeLicenseJob(null, null);
                ackResult.readFromDB(cursor);
                result = ackResult;
                break;

            case DatabaseConstants.JOBTYPE_ACQUIRE_LICENCE:
                AcquireLicenseJob acqResult = new AcquireLicenseJob(null, null);
                acqResult.readFromDB(cursor);
                result = acqResult;
                break;

            case DatabaseConstants.JOBTYPE_DOWNLOAD_CONTENT:
                DownloadContentJob dwlResult = new DownloadContentJob(null);
                dwlResult.readFromDB(cursor);
                result = dwlResult;
                break;

            case DatabaseConstants.JOBTYPE_DRM_FEEDBACK:
                DrmFeedbackJob dialogsResult = new DrmFeedbackJob(0);
                dialogsResult.readFromDB(cursor);
                result = dialogsResult;
                break;

            case DatabaseConstants.JOBTYPE_GET_METERING_CERTIFICATE:
                GetMeteringCertificateJob meteringResult = new GetMeteringCertificateJob(null,
                        null, null);
                meteringResult.readFromDB(cursor);
                result = meteringResult;
                break;

            case DatabaseConstants.JOBTYPE_JOIN_DOMAIN:
                JoinDomainJob joinDomainResult = new JoinDomainJob(null, null, null, null, null);
                joinDomainResult.readFromDB(cursor);
                result = joinDomainResult;
                break;

            case DatabaseConstants.JOBTYPE_LAUNCH_LUIURL_IF_FAIL:
                LaunchLuiUrlIfFailureJob launchResult = new LaunchLuiUrlIfFailureJob(null);
                launchResult.readFromDB(cursor);
                result = launchResult;
                break;

            case DatabaseConstants.JOBTYPE_LEAVE_DOMAIN:
                LeaveDomainJob leaveResult = new LeaveDomainJob(null, null, null, null, null);
                leaveResult.readFromDB(cursor);
                result = leaveResult;
                break;

            case DatabaseConstants.JOBTYPE_PROCESS_METERING_DATA:
                ProcessMeteringDataJob processMeteringResult = new ProcessMeteringDataJob(null,
                        null, null, null, false);
                processMeteringResult.readFromDB(cursor);
                result = processMeteringResult;
                break;

            case DatabaseConstants.JOBTYPE_RENEW_RIGHTS:
                RenewRightsJob renewResult = new RenewRightsJob(null);
                renewResult.readFromDB(cursor);
                result = renewResult;
                break;

            case DatabaseConstants.JOBTYPE_WEB_INITIATOR:
                WebInitiatorJob webResult = new WebInitiatorJob(null);
                webResult.readFromDB(cursor);
                result = webResult;
                break;

            default:
                // error record, return null, should be removed by the caller
                Log.e(TAG, "Error, garbage in the job database");
                // remove(cursor.getInt(DatabaseConstants.COLUMN_POS_ID));
                break;
        }
        return result;
    }

    public long insert(ContentValues values) {
        long result = -1;
        try {
            lock.lock();
            result = sqlDb.insert(DatabaseConstants.DATABASE_TASKS_TABLE_NAME, null, values);
        } catch (SQLiteException e) {
            Log.e(TAG, "Insert: We have a exception -job will not get stored: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        if (Constants.DEBUG) {
            Log.d(TAG, "insert: returning " + result);
        }
        return result;
    }

    public long insertParameter(ContentValues values) {
        long result = -1;
        try {
            lock.lock();
            result = sqlDb.insert(DatabaseConstants.DATABASE_PARAMETERS_TABLE_NAME, null, values);
        } catch (SQLiteException e) {
            Log.e(TAG,
                    "Insert: We have a exception " + "-parameter will not get stored: "
                            + e.getMessage());
        } finally {
            lock.unlock();
        }
        if (Constants.DEBUG) {
            Log.d(TAG, "insert: returning " + result);
        }
        return result;
    }

    /*
     * Function to get the database content will return a Cursor and it's upp to
     * caller to keep track and close cursor. All jobs are
     */

    public Cursor getDatabaseTaskContents() {
        Cursor cursor = null;

        try {
            lock.lock();
            cursor = sqlDb.query(DatabaseConstants.DATABASE_TASKS_TABLE_NAME, null, null, null,
                    null, null, null);
        } catch (SQLiteException e) {
            Log.e(TAG,
                    "getDatabaseContent: We have a exception, will not return any content:"
                            + e.getMessage());
        } finally {
            lock.unlock();
        }
        return cursor;
    }

    public Cursor getDatabaseParameterContents(long sessionId) {
        Cursor cursor = null;
        String args[] = {
            String.valueOf(sessionId)
        };
        try {
            lock.lock();
            cursor = sqlDb.query(DatabaseConstants.DATABASE_PARAMETERS_TABLE_NAME, null,
                    DatabaseConstants.COLUMN_PARAMETERS_NAME_SESSION_ID + " = ?", args, null, null,
                    null);
        } catch (SQLiteException e) {
            Log.e(TAG,
                    "getDatabaseContent: We have a exception, will not return any content:"
                            + e.getMessage());
        } finally {
            lock.unlock();
        }
        return cursor;
    }

    public HashMap<String, Object> getJobParameters(long sessionId) {
        HashMap<String, Object> items = new HashMap<String, Object>();

        Cursor c = null;
        try {
            lock.lock();
            c = getDatabaseParameterContents(sessionId);
            if (c != null) {
                c.moveToFirst();
                while (!c.isAfterLast()) {
                    long jobSessionId = c
                            .getLong(DatabaseConstants.COLUMN_PARAMETERS_POS_SESSION_ID);
                    Log.d(Constants.LOGTAG, "Checking param with sid:" + jobSessionId
                            + " (against " + sessionId + ")");
                    if (jobSessionId == sessionId) {
                        String key = c.getString(DatabaseConstants.COLUMN_PARAMETERS_POS_KEY);
                        int valueType = c
                                .getInt(DatabaseConstants.COLUMN_PARAMETERS_POS_VALUE_TYPE);
                        if (valueType == DatabaseConstants.PARAMETERS_VALUE_TYPE_STRING) {
                            String value = c
                                    .getString(DatabaseConstants.COLUMN_PARAMETERS_POS_VALUE);
                            items.put(key, value);
                        } else if (valueType == DatabaseConstants.PARAMETERS_VALUE_TYPE_INTEGER) {
                            int value = c.getInt(DatabaseConstants.COLUMN_PARAMETERS_POS_VALUE);
                            items.put(key, value);
                        }
                    }
                    c.moveToNext();
                }
                if (Constants.DEBUG) {
                    Log.d(Constants.LOGTAG,
                            "Finished checking for parameters in DB (" + items.size()
                                    + ") total parameters in database: " + c.getCount());
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG,
                    "Got error when trying to read parameters from DB. Error: " + e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
            lock.unlock();
        }
        return items;
    }

    public void purgeDatabase() {

        try {
            lock.lock();
            sqlDb.execSQL("delete from " + DatabaseConstants.DATABASE_TASKS_TABLE_NAME);
            sqlDb.execSQL("delete from " + DatabaseConstants.DATABASE_PARAMETERS_TABLE_NAME);
        } catch (SQLiteException e) {
            Log.e(TAG,
                    "getDatabaseContent: We have a exception, will not empty database:"
                            + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void beginTransaction() {
        lock.lock();
        sqlDb.beginTransaction();
    }

    public void endTransaction() {
        sqlDb.endTransaction();
        lock.unlock();
    }

    public void setTransactionSuccessful() {
        sqlDb.setTransactionSuccessful();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (Constants.DEBUG) {
            Log.d(TAG, "Creating database");
        }
        try {
            lock.lock();
            db.execSQL(DatabaseConstants.SQL_CREATE_DATABASE_TASKS);
            db.execSQL(DatabaseConstants.SQL_CREATE_DATABASE_PARAMETERS);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (Constants.DEBUG) {
            Log.d(TAG, "We are asked to convert from version " + oldVersion + " to new version "
                    + newVersion);
        }
    }
}
