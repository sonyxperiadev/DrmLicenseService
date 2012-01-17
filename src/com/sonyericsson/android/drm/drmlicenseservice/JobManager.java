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

import com.sonyericsson.android.drm.drmlicenseservice.jobs.DrmFeedbackJob;
import com.sonyericsson.android.drm.drmlicenseservice.jobs.StackableJob;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.EmptyStackException;
import java.util.Map;
import java.util.Stack;

public class JobManager extends Thread {
    protected Stack<StackableJob> mJobStack = new Stack<StackableJob>();

    private Context mContext = null;

    private Handler mHandler = null;

    protected Bundle mParameters = null;

    protected IDrmLicenseServiceCallback mCallbackHandler = null;

    private JobManagerFinishCallback mFinishCallback = null;

    private int mId = 0;

    protected boolean isOk = true;

    private int groupCreateCount = 0;

    private int maxGroupCount = 0;

    private int currentGroupId = 0;

    private boolean keepRunning = true;

    private boolean mAllJobsOk = true;

    private DrmJobDatabase mJobDb;

    protected long mSessionId;

    public JobManager(Context context, Handler handler, int id, DrmJobDatabase jobDb) {
        this(context, handler, null, null, id, jobDb);
    }

    public JobManager(Context context, Handler handler, Bundle parameters,
            IDrmLicenseServiceCallback callbackHandler, int id, DrmJobDatabase jobDb) {
        super();
        mContext = context;
        mHandler = handler;
        mParameters = parameters;
        mCallbackHandler = callbackHandler;
        mId = id;
        mJobDb = jobDb;
        mSessionId = System.currentTimeMillis();
    }

    public void pushJob(StackableJob job) {
        job.setJobManager(this);
        if (Constants.DEBUG) {
            Log.d(Constants.LOGTAG, "Adding job " + job + " to group " + groupCreateCount);
        }
        int groupid = currentGroupId;
        if (groupid == 0) {
            groupid = groupCreateCount;
        }
        job.setGroupId(groupid);
        mJobStack.push(job);
        job.writeToDB(mJobDb);
    }

    public void pushJobNoDatabase(StackableJob job) {
        job.setJobManager(this);
        if (Constants.DEBUG) {
            Log.d(Constants.LOGTAG, "Adding job " + job + " (db) to group " + job.getGroupId());
        }
        mJobStack.push(job);
        if (maxGroupCount < job.getGroupId()) {
            maxGroupCount = job.getGroupId();
        }
    }

    public Context getContext() {
        return mContext;
    }

    public boolean getStatus() {
        return isOk;
    }

    public void setMaxJobGroups(int size) {
        maxGroupCount = groupCreateCount = size;

    }

    public void createNewJobGroup() {
        groupCreateCount--;
    }

    public int getNumberOfGroups() {
        return maxGroupCount;
    }

    public boolean getKeepRunning() {
        return keepRunning;
    }

    public IDrmLicenseServiceCallback getCallbackHandler() {
        return mCallbackHandler;
    }

    public void setCallbackHandler(IDrmLicenseServiceCallback callback) {
        mCallbackHandler = callback;
    }

    public void prepareCancel() {
        keepRunning = false;

        HttpClient.prepareCancel(getSessionId());
    }

    public long getSessionId() {
        return mSessionId;
    }

    public boolean isAllJobsOk() {
        return mAllJobsOk;
    }

    protected abstract class JobManagerFinishCallback {
        abstract void isDone(long sessionId);
    }

    public void registerOnFinishCallback(JobManager.JobManagerFinishCallback cb) {
        mFinishCallback = cb;
    }

    @Override
    public void run() {
        int lastFailingGroupId = -1;
        try {
            while (!mJobStack.isEmpty() && keepRunning) {
                do {
                    try {
                        mJobDb.beginTransaction();
                        StackableJob job = mJobStack.pop();
                        currentGroupId = job.getGroupId();
                        if (lastFailingGroupId == -1) {
                            // Check if have a stored id
                            if (hasParameter(Constants.DRM_KEYPARAM_LASTFAIL)) {
                                lastFailingGroupId = getIntParameter(
                                        Constants.DRM_KEYPARAM_LASTFAIL);
                            }
                        }
                        if (currentGroupId == lastFailingGroupId) {
                            if (Constants.DEBUG) {
                                Log.d(Constants.LOGTAG, "Will notify failure to job: "
                                        + job.getClass().getName() + " in group " + currentGroupId);
                            }
                            job.executeAfterEarlierFailure();
                        } else {
                            if (Constants.DEBUG) {
                                Log.d(Constants.LOGTAG, "Will exec job: " + job.getClass().getName()
                                        + " in group " + currentGroupId);
                            }
                            if (!job.executeNormal()) {
                                lastFailingGroupId = currentGroupId;
                                mAllJobsOk = false;
                                if (Constants.DEBUG) {
                                    Log.w(Constants.LOGTAG, "The job " + job.getClass().getName()
                                            + " in group " + currentGroupId + " failed.");
                                }
                            }
                        }
                        if (lastFailingGroupId == currentGroupId) {
                            addParameter(Constants.DRM_KEYPARAM_LASTFAIL, lastFailingGroupId);
                        }
                        job.removeFromDb(mJobDb);
                        mJobDb.setTransactionSuccessful();
                    } finally {
                        mJobDb.endTransaction();
                    }
                    System.gc();
                } while (!mJobStack.isEmpty() && !isReadyToEvaluteKeepRunning());
                removeParameter(Constants.DRM_KEYPARAM_LASTFAIL);
            }
        } catch (EmptyStackException e) {
        }
        if (Constants.DEBUG) {
            Log.d(Constants.LOGTAG, "The job manager is done");
        }
        if (mHandler != null) {
            Message.obtain(mHandler, mId, Boolean.valueOf(isOk)).sendToTarget();
        }

        // The three following lines should be possible to remove later
        if (lastFailingGroupId != -1) {
            isOk = false;
        }

        if (!keepRunning) {
            // We have been cancelled
            pushJobNoDatabase(new DrmFeedbackJob(Constants.PROGRESS_TYPE_CANCELLED));
            mJobStack.pop().executeAfterEarlierFailure();
        }

        // Notify starting app that we are done
        if (mFinishCallback != null) {
            mFinishCallback.isDone(getSessionId());
        }
    }

    private boolean isReadyToEvaluteKeepRunning() {
        int nextGroupId = mJobStack.peek().getGroupId();
        if (nextGroupId == 0 || nextGroupId != currentGroupId) {
            return true;
        } else {
            return false;
        }
    }

    public StackableJob removeJob(String className) {
        StackableJob jobToRemove = null;
        for (StackableJob job : mJobStack) {
            if (job.getClass().getName().equals(className)) {
                jobToRemove = job;
                // Continue to find all matching jobs and only remove the last
                // one since the list is iterated in reverse order.
            }
        }
        if (jobToRemove != null) {
            jobToRemove.removeFromDb(mJobDb);
            mJobStack.remove(jobToRemove);
        }
        return jobToRemove;
    }

    public final Bundle getParameters() {
        if (mParameters != null) {
            return (Bundle)mParameters.clone();
        } else {
            return null;
        }
    }

    public void removeParameter(String key) {
        if (mParameters != null) {
            mParameters.remove(key);
        }

        mJobDb.removeParameter(mSessionId, key);
    }

    public boolean hasParameter(String key) {
        if (mParameters != null) {
            return mParameters.containsKey(key);
        } else {
            return false;
        }
    }

    public String getStringParameter(String key) {
        if (mParameters != null) {
            return mParameters.getString(key);
        } else {
            return null;
        }
    }

    public int getIntParameter(String key) {
        if (mParameters != null) {
            return mParameters.getInt(key);
        } else {
            return 0;
        }
    }

    public Bundle getBundleParameter(String key) {
        if (mParameters != null) {
            return mParameters.getBundle(key);
        } else {
            return null;
        }
    }

    public void addParameter(String key, String value) {
        if (value != null) {
            if (mParameters == null) {
                mParameters = new Bundle();
            }
            mParameters.putString(key, value);
        }

        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_SESSION_ID, mSessionId);
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_KEY, key);
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_VALUE_TYPE,
                DatabaseConstants.PARAMETERS_VALUE_TYPE_STRING);
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_VALUE, value);

        long result = mJobDb.insertParameter(values);
        if (Constants.DEBUG) {
            Log.d(Constants.LOGTAG, "Added s-parameter to db : " + key + "->" + value + " (res="
                    + result + ")");
        }
    }

    public void addParameter(String key, int value) {
        if (mParameters == null) {
            mParameters = new Bundle();
        }
        mParameters.putInt(key, value);

        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_SESSION_ID, mSessionId);
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_KEY, key);
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_VALUE_TYPE,
                DatabaseConstants.PARAMETERS_VALUE_TYPE_INTEGER);
        values.put(DatabaseConstants.COLUMN_PARAMETERS_NAME_VALUE, value);

        long result = mJobDb.insertParameter(values);
        if (Constants.DEBUG) {
            Log.d(Constants.LOGTAG, "Added i-parameter to db : " + key + "->" + value + " (res="
                    + result + ")");
        }
    }

    public void addParameter(String key, Bundle value) {
        if (value != null) {
            if (mParameters == null) {
                mParameters = new Bundle();
            }
            mParameters.putBundle(key, value);
        }
    }

    public void restoreState() {
        Map<String, ?> items = mJobDb.getJobParameters(mSessionId);
        for (String key : items.keySet()) {
            Object value = items.get(key);
            if (mParameters == null) {
                mParameters = new Bundle();
            }
            Log.d(Constants.LOGTAG, "Restoring parameter from db: " + key + "=" + value);
            if (value instanceof String) {
                mParameters.putString(key, (String)value);
            } else if (value instanceof Integer) {
                mParameters.putInt(key, (Integer)value);
            }
        }
        Log.d(Constants.LOGTAG, "Restored " + items.size() + " parameters from db");
    }
}
