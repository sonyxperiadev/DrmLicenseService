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
 * Sony Ericsson Mobile Communications AB. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.Constants;
import com.sonyericsson.android.drm.drmlicenseservice.CustomDataParser;
import com.sonyericsson.android.drm.drmlicenseservice.ErrorMessageParser;
import com.sonyericsson.android.drm.drmlicenseservice.HttpClient;
import com.sonyericsson.android.drm.drmlicenseservice.JobManager;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;
import com.sonyericsson.android.drm.drmlicenseservice.ErrorMessageParser.ErrorData;
import com.sonyericsson.android.drm.drmlicenseservice.HttpClient.Response;

import android.database.Cursor;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;
import android.drm.DrmManagerClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class StackableJob {
    protected JobManager mJobManager = null;

    private int mGroupId = 0;

    private long mDatabaseId = -1;

    public void setJobManager(JobManager jobManager) {
        mJobManager = jobManager;
    }

    public void setGroupId(int groupId) {
        mGroupId = groupId;
    }

    public int getGroupId() {
        return mGroupId;
    }

    protected long getDatabaseId() {
        return mDatabaseId;
    }

    public void setDatabaseId(long dbId) {
        this.mDatabaseId = dbId;
    }

    public String getType() {
        return getClass().getName().replaceAll(".*\\.", "").replaceAll("Job", "");
    }

    public DrmInfo sendInfoRequest(DrmInfoRequest request) {
        DrmManagerClient dmc = new DrmManagerClient(mJobManager.getContext());
        DrmInfo reply = dmc.acquireDrmInfo(request);
        return reply;
    }

    protected void addCustomData(DrmInfoRequest request, String xmlCustomData) {
        String customData = "";

        if (xmlCustomData != null) {
            customData = xmlCustomData;
        }
        if (mJobManager.getParameters() != null) {
            if (mJobManager.getParameters().containsKey("CUSTOM_DATA")) {
                customData = mJobManager.getParameters().getString("CUSTOM_DATA");
                if (customData != null) {
                    if (mJobManager.getParameters().containsKey("CUSTOM_DATA_PREFIX")) {
                        String customDataPrefix = mJobManager.getParameters().getString(
                                "CUSTOM_DATA_PREFIX");
                        if (customDataPrefix != null) {
                            customData = customDataPrefix + customData;
                        }
                    }
                    if (mJobManager.getParameters().containsKey("CUSTOM_DATA_SUFFIX")) {
                        String customDataSuffix = mJobManager.getParameters().getString(
                                "CUSTOM_DATA_SUFFIX");
                        if (customDataSuffix != null) {
                            customData = customData + customDataSuffix;
                        }
                    }
                }
            }
        }
        if (customData != null && customData.length() > 0) {
            request.put(Constants.DRM_CUSTOM_DATA, customData);
        }
    }

    protected boolean postMessage(String url, String data) {
        url = url.replaceAll("&amp;", "&");
        if (!mJobManager.getKeepRunning()) {
            return false;
        }
        HttpClient.Response response = HttpClient.post(mJobManager.getSessionId(), url,
                getType(), data, mJobManager.getParameters());
        return handleResponse(response);
    }

    public boolean handleResponse(Response response) {
        boolean isOk = false;
        if (response != null) {
            switch (response.getStatus()) {
                case 200:
                    // Log.d(Constants.LOGTAG,
                    // "Response is ok, sending to PK...");
                    isOk = handleResponse200(response.getData());
                    if (isOk) {
                        String customData = new CustomDataParser()
                                .parseXML(response.getByteData());
                        // Log.d("test", "Customdata returned from server was: "
                        // + customData);
                        if (customData != null) {
                            mJobManager.addParameter("CUSTOM_DATA_USED", customData);
                        }
                    }
                    break;
                case 500:
                    boolean messageHandled = false;
                    // Log.w(Constants.LOGTAG,
                    // "500 - Error message from server");
                    ErrorData errorData = new ErrorMessageParser()
                            .parseXML(response.getByteData());
                    String statusCode = null;
                    if (errorData != null) {
                        String customData = errorData.getValue("CustomData");
                        // Log.d("test",
                        // "Customdata in errorcode returned from server was: "
                        // + customData);
                        if (customData != null) {
                            mJobManager.addParameter("CUSTOM_DATA_USED", customData);
                        }

                        statusCode = errorData.getValue("StatusCode");
                        // Log.w(Constants.LOGTAG, "Status code from server:" +
                        // statusCode);
                        if (statusCode != null && statusCode.length() > 0) {
                            Method m = null;
                            try {
                                m = this.getClass().getMethod(
                                        "handleError" + statusCode.toLowerCase(), new Class[] {
                                            ErrorData.class
                                        });
                            } catch (SecurityException e) {
                            } catch (NoSuchMethodException e) {
                            }
                            if (m != null) {
                                try {
                                    Object result = m.invoke(this, new Object[] {
                                        errorData
                                    });
                                    if (result != null && result instanceof Boolean) {
                                        isOk = ((Boolean)result).booleanValue();
                                    }
                                    messageHandled = true;
                                } catch (IllegalArgumentException e) {
                                } catch (IllegalAccessException e) {
                                } catch (InvocationTargetException e) {
                                }
                            }
                        }
                    } else {
                        // XML parsing failed
                        mJobManager.addParameter("HTTP_ERROR", -5);
                        mJobManager.addParameter("INNER_HTTP_ERROR", response.getStatus());
                        messageHandled = true;
                    }
                    if (!messageHandled) {
                        isOk = sendToResponseCodeHandler(response);
                        if (isOk == false) {
                            mJobManager.addParameter("HTTP_ERROR", response.getStatus());
                            if (errorData != null) {
                                String redirectUrl = errorData.getValue("RedirectUrl");
                                if (redirectUrl != null && redirectUrl.length() > 0) {
                                    mJobManager.addParameter("REDIRECT_URL", redirectUrl);
                                }
                            }
                            if (statusCode != null && statusCode.length() > 0) {
                                int value = Long.decode(statusCode).intValue();
                                mJobManager.addParameter("INNER_HTTP_ERROR", value);
                            }
                        }
                    }
                    break;
                default:
                    // Log.d(Constants.LOGTAG,
                    // "Server request failed failed with code " +
                    // response.getStatus());
                    mJobManager.addParameter("HTTP_ERROR", response.getStatus());
                    if (response.getInnerStatus() != 0) {
                        mJobManager.addParameter("INNER_HTTP_ERROR", response.getInnerStatus());
                    }

                    isOk = sendToResponseCodeHandler(response);

                    break;
            }
        }
        return isOk;
    }

    private boolean sendToResponseCodeHandler(Response response) {
        Method m = null;
        boolean isOk = false;
        try {
            m = this.getClass().getMethod("handleResponse" + response.getStatus(), new Class[] {
                String.class
            });
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        }
        if (m != null) {
            try {
                Object result = m.invoke(this, new Object[] {
                    response.getData()
                });
                if (result != null && result instanceof Boolean) {
                    isOk = ((Boolean)result).booleanValue();
                }
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        return isOk;
    }

    protected boolean handleResponse200(String data) {
        return false;
    }

    /**
     * Perform the actions required by the task.
     *
     * @return Status of the job. If false is returned no further jobs will be
     *         executed.
     */
    public abstract boolean executeNormal();

    /**
     * Writes the job to the job database.
     *
     * @return Status of the job. If false is returned, nothing was written to
     *         the db
     */
    public abstract boolean writeToDB(DrmJobDatabase msDb);

    /**
     * Reads the job from the job database
     *
     * @return Status of the read.
     */
    public abstract boolean readFromDB(Cursor c);

    /**
     * Removes the job from the job database
     *
     * @return Status of the remove.
     */
    public boolean removeFromDb(DrmJobDatabase msDb) {
        if (getDatabaseId() != -1) {
            return msDb.remove(getDatabaseId());
        }
        return false;
    }

    /**
     * Notify the task that the job will not be executed. Gives possibilities to
     * clean up or send messages.
     */
    public void executeAfterEarlierFailure() {
    }
}
