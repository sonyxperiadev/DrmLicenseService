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

import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.media.MediaDrm;
import android.content.Context;
import android.os.AsyncTask;
import android.content.ComponentName;

/* This service schedules GarbageCollection of expired licenses.
 * For it to work the CDM needs to support property "garbage-collect"
 * in getPropertyString and setPropertyString.
 * Where getPropertyString returns the time in millis for when last garbage
 * collect was performed. And setPropertyString triggers garbage collection,
 * preferably only if there are no open sessions to avoid concurrent calls
 * to license database.
 * If unsupported in CDM this call will simply exit due to IllegalArgumentException
 * from getpropertyString.
 */

public class GarbageJobService extends JobService {

    private static final String mGarbageCollectProperty = "garbage-collect";

    private GarbageAsyncTask garbageTask;

    private GarbageJobService mService;

    @Override
    public void onCreate() {
        DrmLog.debug("start");
        super.onCreate();
        mService = this;
        garbageTask = new GarbageAsyncTask();
        DrmLog.debug("end");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        DrmLog.debug("start");
        garbageTask.execute(params);
        DrmLog.debug("end");
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        DrmLog.debug("start");
        return false; // rescheduling is handled locally
    }

    public void reschedule() {
        DrmLog.debug("start");
        ScheduleGarbageCollection(getBaseContext());
        DrmLog.debug("end");
    }

    public static void ScheduleGarbageCollection(Context context) {
        DrmLog.debug("start");
        boolean isSupported = false, isNeeded = false;
        try {
            MediaDrm md = new MediaDrm(Constants.UUID_PR);
            String res = md.getPropertyString(mGarbageCollectProperty);
            // times are in seconds
            long lastGc = Long.parseLong(res);
            long currentTime = System.currentTimeMillis() / 1000;
            DrmLog.debug("last: " + lastGc + " currentTime: " + currentTime + " diff: " +
                (currentTime - lastGc) + " needed: " + Constants.GARBAGE_COLLECT_INTERVAL);
            isSupported = true;
            isNeeded = Constants.DEBUG ||
                    (Constants.GARBAGE_COLLECT_INTERVAL > currentTime - lastGc);
        } catch (Exception e) {
            DrmLog.logException(e);
        }

        if (isSupported && isNeeded) {
            ComponentName name = new ComponentName(context, GarbageJobService.class);
            JobInfo jobInfo = new JobInfo.Builder( /*jobs created with the same jobId,
                will update the pre-existing job with the same id*/ 0, name)
                .setRequiresDeviceIdle(true)
                .setMinimumLatency(3600000) // wait at least 1 h
                //.setOverrideDeadline(Constants.GARBAGE_COLLECT_INTERVAL * 1000) // debug
                .build();
            JobScheduler scheduler = (JobScheduler) context.getSystemService(
                    Context.JOB_SCHEDULER_SERVICE);
            if (scheduler.schedule(jobInfo) != JobScheduler.RESULT_SUCCESS) {
                DrmLog.debug("Failed to schedule garbage collection");
            }
        }
        DrmLog.debug("end");
    }

    private class GarbageAsyncTask extends AsyncTask<JobParameters, Void, JobParameters[]> {

        private boolean mNeedsRescheduling = true;

        @Override
        protected JobParameters[] doInBackground(JobParameters... params) {
            DrmLog.debug("start");
            boolean status = false;
            try {
                MediaDrm md = new MediaDrm(Constants.UUID_PR);
                String res = md.getPropertyString(mGarbageCollectProperty);
                // times are in seconds
                long lastGc = Long.parseLong(res);
                long currentTime = System.currentTimeMillis() / 1000;
                md.setPropertyString(mGarbageCollectProperty, "true");
                long newGc = Long.parseLong(md.getPropertyString(mGarbageCollectProperty));
                DrmLog.debug("call returned, new gc time: " + newGc);
                if (newGc > lastGc) {
                    status = true;
                }
                md.release();
            } catch (Exception e) {
                status = true; // unsupported, do not reschedule
                DrmLog.logException(e);
            }
            mNeedsRescheduling = !status;
            mService.jobFinished(params[0], false); // handle rescheduling locally
            if (mNeedsRescheduling) {
                mService.reschedule();
            }
            DrmLog.debug("status:" + status);
            DrmLog.debug("end");
            return params;
        }

        public boolean getJobStatus() {
            DrmLog.debug("start");
            return mNeedsRescheduling;
        }
    }
}

