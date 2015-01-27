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

import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton to handle sessions and callbacks.
 */
public class SessionManager {

    private static SessionManager s_mapper = null;

    private ArrayList<Long> mSessions;
    private ArrayList<Long> mCancelledSessions;
    private ArrayList<StoredCallback> mStoredCallbacks;
    private LongSparseArray<IDrmLicenseServiceCallback> mCallbackHandlers;
    private LongSparseArray<Bundle> mTrafficParameters;
    private LongSparseArray<Boolean> mGroupStatus;

    private static final int MAX_NUMBER_CALLBACKS = 100;

    private ReentrantLock mLock = new ReentrantLock();

    private SessionManager() {
        mCallbackHandlers = new LongSparseArray<IDrmLicenseServiceCallback>();
        mTrafficParameters = new LongSparseArray<Bundle>();
        mCancelledSessions = new ArrayList<Long>();
        mSessions = new ArrayList<Long>();
        mGroupStatus = new LongSparseArray<Boolean>();
        mStoredCallbacks = new ArrayList<SessionManager.StoredCallback>();
    }

    /**
     * Disabled, throws UnsupportedOperationException
     */
    @Override
    public SessionManager clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * Retrieve singleton. Creates new if non-existing.
     *
     * @return singleton
     */
    public synchronized static SessionManager getInstance() {
        if (s_mapper == null) {
            s_mapper = new SessionManager();
        }
        return s_mapper;
    }

    // Concurrency controlled public functions

    /**
     * Starts a new unique session, and maps callback and parameters.
     *
     * @param callbackhandler for service callbacks
     * @param parameters for http traffic
     * @return session ID
     */
    public long startSession(IDrmLicenseServiceCallback callbackhandler, Bundle parameters) {
        long sessionId = -1;
        while (sessionId == -1) {
            sessionId = System.currentTimeMillis();
            try {
                mLock.lock();
                if (!mSessions.contains(sessionId)) {
                    mSessions.add(sessionId);
                    map(sessionId, callbackhandler, parameters);
                } else {
                    sessionId = -1;
                }
            } finally {
                mLock.unlock();
            }
            if (sessionId == -1) {
                // Since sessionId is bases on system time,
                // we sleep 1 millis if we happened get an existing sessionId
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }
        return sessionId;
    }

    /**
     * If service has lost mapping, this function reopens a closed session
     * so that it's possible to reconnect callback and retrieve stored and future callbacks.
     *
     * @param sessionId
     */
    public void makeSureAIDLSessionIsOpen(long sessionId) {
        try {
            mLock.lock();
            if (sessionId > Constants.NOT_AIDL_SESSION && !mSessions.contains(sessionId)) {
                mSessions.add(sessionId);
            }
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Connect callback and parameters to existing session.
     *
     * @param sessionId Session for mapping
     * @param callbackHandler Handler of callbacks
     * @param parameters parameters for http requests
     * @return true if session exists, false otherwise
     */
    public boolean connectToSession(long sessionId, IDrmLicenseServiceCallback callbackHandler,
            Bundle parameters) {
        boolean res = false;
        try {
            mLock.lock();
            if (mSessions.contains(sessionId)) {
                map(sessionId, callbackHandler, parameters);
                res = true;
            }
        } finally {
            mLock.unlock();
        }
        return res;
    }

    /**
     * Retrieves HTTP parameters for session.
     *
     * @param sessionId dls session id
     * @return Bundle with HTTP parameters for session, null if no parameters
     *         specified in original request.
     */
    public Bundle getHttpParams(long sessionId) {
        Bundle parameters = null;
        try {
            mLock.lock();
            parameters = mTrafficParameters.get(sessionId);
        } finally {
            mLock.unlock();
        }
        return parameters;
    }

    /**
     * Cancel session. Aborts any HTTP requests in progress. And prevents any
     * other tasks in queue for this session to be executed.
     *
     * @param sessionId dls session id
     * @return false if there is no such session, otherwise session is
     *         cancelled and true returned.
     */
    public boolean cancel(long sessionId) {
        boolean res = false;
        try {
            mLock.lock();
            res = mSessions.contains(sessionId);
            if (res) {
                mCancelledSessions.add(sessionId);
            }
        } finally {
            mLock.unlock();
        }

        DLSHttpClient.prepareCancel(sessionId);
        return res;
    }

    /**
     * Checks if a session has been cancelled.
     *
     * @param sessionId dls session id
     * @return true if session has been cancelled, otherwise false
     */
    public boolean isCancelled(long sessionId) {
        boolean isCancelled = false;
        try {
            mLock.lock();
            isCancelled = mCancelledSessions.contains(sessionId);
        } finally {
            mLock.unlock();
        }
        return isCancelled;
    }

    /**
     * Removed a sessionId from list of cancelled sessions.
     * Called when all task for a session has left quene.
     *
     * @param sessionId dls session id
     */
    public void clearCancelled(long sessionId) {
        try {
            mLock.lock();
            mCancelledSessions.remove(sessionId);
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Removes all CallbackHandlers, Should be called when DrmLicenseService is
     * stopped and destroyed
     */
    public void clearDeadObjects() {
        DrmLog.debug("clearDeadObjects");
        try {
            mLock.lock();
            mCallbackHandlers.clear();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Return progress information about requests made. If there is no
     * CallbackHandler connected progress reports will be stored until a new
     * handler is connected to session.
     *
     * @param sessionId id for request
     * @param state integer value defining kind of request
     * @param status true for successful requests, otherwise false
     * @param parameters specific callback values
     */
    public void callback(long sessionId, int state, boolean status, Bundle parameters) {
        try {
            mLock.lock();
            tryToSendCallback(buildCallback(sessionId, state, status, parameters));
        } finally {
            mLock.unlock();
        }
    }

    // private function, concurrency from public functions

    /*
     * Not thread safe, lock handled in public functions
     */
    private void map(long sessionId, IDrmLicenseServiceCallback callbackhandler, Bundle parameters)
            {
        DrmLog.debug("Mapping callbackHandler and httpParameters for sessionId: " + sessionId);
        // Add/update session values
        mTrafficParameters.put(sessionId, parameters);
        if (callbackhandler != null) {
            mCallbackHandlers.put(sessionId, callbackhandler);
            reportNonReportedCallback(sessionId);
        }
    }

    /**
     * Check if there is a callback handler connected to a specific session
     *
     * @param sessionId dls session id
     * @return true if session exists
     */
    public boolean hasCallbackHandler(long sessionId) {
        boolean res = false;
        try {
            mLock.lock();
            res = mCallbackHandlers.get(sessionId, null) != null;
        } finally {
            mLock.unlock();
        }
        return res;
    }

    /*
     * Not thread safe, lock handled in public functions
     */
    private StoredCallback buildCallback(long sessionId, int state, boolean status,
            Bundle parameters) {
        Bundle reportParameters = new Bundle();
        int groups = parameters.getInt(Constants.DRM_KEYPARAM_GROUP_COUNT, -1);
        String path = parameters.getString(Constants.DLS_CB_PATH);

        switch (state) {
            case Constants.PROGRESS_TYPE_RENEW_RIGHTS:
                copyGenericParameters(reportParameters, parameters);
                String header = parameters.getString(Constants.DRM_KEYPARAM_RENEW_HEADER, null);
                if (header != null) {
                    reportParameters.putString(Constants.DRM_KEYPARAM_RENEW_HEADER, header);
                }
                byte[] pssh = parameters.getByteArray(Constants.DRM_KEYPARAM_RENEW_PSSH_BOX);
                if (pssh != null) {
                    reportParameters.putByteArray(Constants.DRM_KEYPARAM_PSSHBOX, pssh);
                }
                if (path != null) {
                    reportParameters.putString(Constants.DRM_KEYPARAM_FILEPATH, path);
                }
                break;
            case Constants.PROGRESS_TYPE_FINISHED_JOB:
                updateGroupStatus(sessionId, status);
                copyGenericParameters(reportParameters, parameters);
                int group = parameters.getInt(Constants.DRM_KEYPARAM_GROUP_NUMBER, -1);
                String groupType = parameters.getString(Constants.DRM_KEYPARAM_TYPE);
                reportParameters.putInt(Constants.DRM_KEYPARAM_GROUP_COUNT, groups);
                reportParameters.putInt(Constants.DRM_KEYPARAM_GROUP_NUMBER, group);
                if (groupType != null && groupType.length() > 0) {
                    reportParameters.putString(Constants.DRM_KEYPARAM_TYPE, groupType);
                }
                if (path != null) {
                    reportParameters.putString(Constants.DRM_KEYPARAM_CONTENT_URL, path);
                }
                break;
            case Constants.PROGRESS_TYPE_WEBINI_COUNT:
                updateGroupStatus(sessionId, status);
                reportParameters.putInt(Constants.DRM_KEYPARAM_GROUP_COUNT, groups);
                if (path != null) {
                    reportParameters.putString(Constants.DRM_KEYPARAM_WEB_INITIATOR, path);
                }
                if (!status) {
                    copyGenericParameters(reportParameters, parameters);
                }
                break;
            case Constants.PROGRESS_TYPE_FINISHED_WEBINI:
                status = getGroupStatus(sessionId);
                break;
            case Constants.PROGRESS_TYPE_CANCELLED:
                break;
            case Constants.PROGRESS_TYPE_HTTP_RETRYING:
                copyGenericParameters(reportParameters, parameters);
                String url = parameters.getString(Constants.DRM_KEYPARAM_URL);
                if (url != null) {
                    reportParameters.putString(Constants.DRM_KEYPARAM_URL, url);
                }
                break;
            default:
                return null;
        }

        return new StoredCallback(sessionId, state, status, reportParameters);
    }

    /*
     * Not thread safe, lock handled in public function map
     */
    private boolean tryToSendCallback(StoredCallback cb) {
        boolean status = true;
        if (cb != null) {
            IDrmLicenseServiceCallback callbackHandler = mCallbackHandlers.get(cb.mSessionId, null);
            if (callbackHandler != null) {
                DrmLog.debug("reportParameters" + cb.mParameters.toString());
                try {
                    callbackHandler.onProgressReport(cb.mSessionId, cb.mState,
                            cb.mStatus, cb.mParameters);
                    if (cb.mState == Constants.PROGRESS_TYPE_CANCELLED ||
                            cb.mState == Constants.PROGRESS_TYPE_FINISHED_WEBINI ||
                            cb.mState == Constants.PROGRESS_TYPE_RENEW_RIGHTS) {
                        clearSession(cb.mSessionId);
                    }
                } catch (DeadObjectException e) {
                    storeCallback(cb);
                    DrmLog.logException(e);
                    status = false;
                } catch (RemoteException e) {
                    DrmLog.logException(e);
                    status = false;
                }
            } else {
                DrmLog.debug("No callback handler connected, store callback");
                storeCallback(cb);
                status = false;
            }
        }
        DrmLog.debug("tryToSendCallback status " + status);
        return status;
    }

    /*
     * Not thread safe, lock handled in public function map
     */
    private void storeCallback(StoredCallback newCallback) {
        mStoredCallbacks.add(newCallback);
        if (mStoredCallbacks.size() > MAX_NUMBER_CALLBACKS) {
            StoredCallback dequenedCallback = mStoredCallbacks.remove(0);
            if (dequenedCallback.mState == Constants.PROGRESS_TYPE_FINISHED_WEBINI ||
                    dequenedCallback.mState == Constants.PROGRESS_TYPE_CANCELLED ||
                    dequenedCallback.mState == Constants.PROGRESS_TYPE_RENEW_RIGHTS) {
                getGroupStatus(dequenedCallback.mSessionId);
                clearSession(dequenedCallback.mSessionId);
                clearCancelled(dequenedCallback.mSessionId);
            }
            DrmLog.debug("dropping callback");
        }
    }

    /*
     * Not thread safe, lock handled in public function map
     */
    private void reportNonReportedCallback(long sessionId) {
        DrmLog.debug("checkForNonReportedCallback " + mStoredCallbacks.size());
        for (int i = 0; i < mStoredCallbacks.size(); i++) {
            if (mStoredCallbacks.get(i).mSessionId == sessionId) {
                StoredCallback callback = mStoredCallbacks.get(i);
                boolean status = tryToSendCallback(callback);
                if (status) {
                    mStoredCallbacks.remove(i);
                    i--;
                } else {
                    break;
                }
            }
        }
    }

    /*
     * Called when WebInitiator/RenewRights finished or got cancelled. Clears
     * information about session.
     *
     * NOTE this function does not clear cancelled-list of sessionId, since we
     * might need to know if session is cancelled for other tasks in same
     * session
     *
     * Not thread safe, lock handled in public function callback/map
     */
    private void clearSession(long sessionId) {
        mCallbackHandlers.remove(sessionId);
        mTrafficParameters.remove(sessionId);
        mSessions.remove(sessionId);
        mGroupStatus.remove(sessionId);
    }

    /*
     * Not thread safe, lock handled in public function callback/map
     */
    private void updateGroupStatus(long sessionId, boolean status) {
        mGroupStatus.put(sessionId, (mGroupStatus.get(sessionId, true) && status));
    }

    /*
     * Not thread safe, lock handled in public function callback/map
     */
    private boolean getGroupStatus(long sessionId) {
        boolean status = mGroupStatus.get(sessionId, true);
        mGroupStatus.delete(sessionId);
        return status;
    }

    /*
     * Not thread safe, lock handled in public function callback/map
     */
    private void copyGenericParameters(Bundle outParameters, Bundle inParameters) {
        String customData = inParameters.getString(Constants.DRM_KEYPARAM_CUSTOM_DATA);
        if (customData != null) {
            outParameters.putString(Constants.DRM_KEYPARAM_CUSTOM_DATA, customData);
        }
        String redirectUrl = inParameters.getString(Constants.DRM_KEYPARAM_REDIRECT_URL);
        if (redirectUrl != null && redirectUrl.length() > 0) {
            outParameters.putString(Constants.DRM_KEYPARAM_REDIRECT_URL, redirectUrl);
        }
        int httpError = inParameters.getInt(Constants.DRM_KEYPARAM_HTTP_ERROR, 0);
        if (httpError != 0) {
            outParameters.putInt(Constants.DRM_KEYPARAM_HTTP_ERROR, httpError);
        }
        int innerHttpError = inParameters.getInt(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR, 0);
        if (innerHttpError != 0) {
            outParameters.putInt(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR, innerHttpError);
        }
    }

    private static class StoredCallback {
        long mSessionId;
        int mState;
        boolean mStatus;
        Bundle mParameters;

        public StoredCallback(long sessionId, int state, boolean status, Bundle parameters) {
            mSessionId = sessionId;
            mState = state;
            mStatus = status;
            mParameters = parameters;
        }
    }
}
