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

import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

import android.app.job.JobScheduler;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;

/**
 * Service for handling license renewal. Handles two intent actions - renew and
 * save license.
 */
public class DrmLicenseService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        String intentAction;
        if (intent != null && (intentAction = intent.getAction()) != null) {
            if (intentAction.equals(Constants.INTENT_ACTION_DRM_SERVICE_RENEW)) {
                renewRights(intent);
            } else if (intentAction
                    .equals(Constants.INTENT_ACTION_DRM_SERVICE_HANDLE_WEB_INITIATOR)) {
                handleWebInitiator(intent);
            } else if (intentAction.equals(DrmLicenseActivity.class.getName())) {
                // DrmLicenseActivity is calling for renewal of rights
                String type = intent.getType();
                if (type != null &&
                        type.equalsIgnoreCase(Constants.DRM_DLS_INITIATOR_MIME)) {
                    handleWebInitiator(intent);
                }
            } else {
                DrmLog.debug("Action is not supported");
            }
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        SessionManager.getInstance().clearDeadObjects();
        super.onDestroy();
        GarbageJobService.ScheduleGarbageCollection(this);
        DrmLog.debug("Service stopped and destroyed");
    }

    private void renewRights(final Intent intent) {
        byte[] psshBox = intent.getByteArrayExtra(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX);
        String header = intent.getStringExtra(Constants.DRM_KEYPARAM_RENEW_HEADER);
        final Uri uri = intent.getData();
        if (header != null || psshBox != null || uri != null) {
            Intent serviceintent = new Intent(Constants.TASK_SERVICE);
            serviceintent.putExtra(Constants.DLS_INTENT_REQUEST_TYPE,
                    RequestManager.TYPE_RENEW_RIGHTS);
            if (header != null) {
                serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_HEADER, header);
            } else if (psshBox != null) {
                serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX, psshBox);
            } else {
                serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_FILE_URI, uri.toString());
            }
            serviceintent.setClass(this, DrmLicenseTaskService.class);
            startService(serviceintent);
        }
    }

    private void handleWebInitiator(final Intent intent) {
        final Uri uri = intent.getData();
        String mime = intent.getType();
        if (mime != null && mime.equals(Constants.DRM_DLS_INITIATOR_MIME)) {
            if (uri != null && uri.getScheme() != null) {
                intent.setAction(Constants.WEBI_SERVICE);
                intent.setClass(getBaseContext(), WebInitiatorTaskService.class);
                getBaseContext().startService(intent);
                int resId = R.string.status_start_download;
                Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }

    private final IDrmLicenseService.Stub mBinder = new IDrmLicenseService.Stub() {
        public long handleWebInitiator(Uri uri, Bundle parameters,
                IDrmLicenseServiceCallback callbackHandler) throws RemoteException {
            long sessionId = 0;
            if (uri != null) {
                sessionId = SessionManager.getInstance().startSession(callbackHandler, parameters);
                Intent serviceintent = new Intent(Constants.WEBI_SERVICE);
                serviceintent.setData(uri);
                serviceintent.putExtra(Constants.DLS_INTENT_SESSION_ID, sessionId);
                serviceintent.setClass(getBaseContext(), WebInitiatorTaskService.class);
                startService(serviceintent);
            }
            return sessionId;
        }

        public long renewRights(Uri uri, Bundle parameters,
                IDrmLicenseServiceCallback callbackHandler) throws RemoteException {
            long sessionId = 0;
            if (uri != null) {
                sessionId = SessionManager.getInstance().startSession(callbackHandler, parameters);
                Intent serviceintent = new Intent(Constants.TASK_SERVICE);
                serviceintent.putExtra(Constants.DLS_INTENT_REQUEST_TYPE,
                        RequestManager.TYPE_RENEW_RIGHTS);
                serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_FILE_URI, uri.toString());
                serviceintent.putExtra(Constants.DLS_INTENT_SESSION_ID, sessionId);

                Bundle callbackParameters = new Bundle();
                callbackParameters.putInt(Constants.DLS_CB_PROGRESS_TYPE,
                        Constants.PROGRESS_TYPE_RENEW_RIGHTS);
                callbackParameters.putString(Constants.DLS_CB_PATH, uri.toString());

                serviceintent.putExtra(Constants.DLS_INTENT_CB_PARAMS, callbackParameters);
                serviceintent.putExtra(Constants.DLS_INTENT_HTTP_PARAMS, parameters);
                serviceintent.setClass(getBaseContext(), DrmLicenseTaskService.class);
                startService(serviceintent);
            }
            return sessionId;
        }

        @Override
        public long renewRightsExt(Bundle renewData, Bundle parameters,
                IDrmLicenseServiceCallback callbackHandler) throws RemoteException {
            long sessionId = 0;
            if (renewData != null) {

                byte[] psshBox = renewData.getByteArray(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX);
                String header = renewData.getString(Constants.DRM_KEYPARAM_RENEW_HEADER);
                String filePath = renewData.getString(Constants.DRM_KEYPARAM_RENEW_FILE_PATH);

                if (header != null || psshBox != null || filePath != null) {
                    sessionId = SessionManager.getInstance().startSession(callbackHandler,
                            parameters);
                    Intent serviceintent = new Intent(Constants.TASK_SERVICE);
                    serviceintent.putExtra(Constants.DLS_INTENT_REQUEST_TYPE,
                            RequestManager.TYPE_RENEW_RIGHTS);
                    serviceintent.putExtra(Constants.DLS_INTENT_SESSION_ID, sessionId);

                    Bundle callbackParameters = new Bundle();
                    callbackParameters.putInt(Constants.DLS_CB_PROGRESS_TYPE,
                            Constants.PROGRESS_TYPE_RENEW_RIGHTS);
                    if (header != null) {
                        serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_HEADER, header);
                        callbackParameters.putString(Constants.DRM_KEYPARAM_RENEW_HEADER, header);
                    } else if (psshBox != null) {
                        serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX, psshBox);
                        callbackParameters.putByteArray(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX,
                                psshBox);
                    } else {
                        serviceintent.putExtra(Constants.DRM_KEYPARAM_RENEW_FILE_URI, filePath);
                        callbackParameters.putString(Constants.DLS_CB_PATH, filePath);
                    }
                    serviceintent.putExtra(Constants.DLS_INTENT_CB_PARAMS, callbackParameters);
                    serviceintent.putExtra(Constants.DLS_INTENT_HTTP_PARAMS, parameters);
                    serviceintent.setClass(getBaseContext(), DrmLicenseTaskService.class);
                    startService(serviceintent);
                }
            }
            return sessionId;
        }

        @Override
        public boolean setCallbackListener(IDrmLicenseServiceCallback callbackHandler,
                long sessionId, Bundle parameters) throws RemoteException {
            return SessionManager.getInstance().connectToSession(sessionId, callbackHandler,
                    parameters);
        }

        @Override
        public boolean cancelSession(long sessionId) throws RemoteException {
            return SessionManager.getInstance().cancel(sessionId);
        }
    };

}
