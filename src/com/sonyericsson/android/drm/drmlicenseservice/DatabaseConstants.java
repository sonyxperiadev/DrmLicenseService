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

package com.sonyericsson.android.drm.drmlicenseservice;

public class DatabaseConstants {
    /**
     * Database name
     */
    public static final String DATABASE_NAME = "job_tasks.db";

    /**
     * Database table name
     */
    public static final String DATABASE_TABLE_NAME = "job_tasks";

    /**
     * The database version
     */
    protected static final int DATABASE_VERSION = 1;

    /**
     * flag for having the database to open writable
     */
    public static final boolean DATABASE_WRITABLE = true;

    /**
     * flag for having the database to open writable
     */
    public static final boolean DATABASE_READABLE = !DATABASE_WRITABLE;

    /**
     * General defines of the columns in the database
     */
    public static final String COLUMN_NAME_ID = "id";

    public static final String COLUMN_NAME_TYPE = "type";

    public static final String COLUMN_NAME_GRP_ID = "groupid";

    public static final String COLUMN_NAME_SESSION_ID = "sessionid";

    public static final String COLUMN_NAME_GENERAL1 = "general1";

    public static final String COLUMN_NAME_GENERAL2 = "general2";

    public static final String COLUMN_NAME_GENERAL3 = "general3";

    public static final String COLUMN_NAME_GENERAL4 = "general4";

    public static final String COLUMN_NAME_GENERAL5 = "general5";

    /**
     * SQL command to create the database
     */
    protected static final String SQL_CREATE_DATABASE =
        "CREATE TABLE " + DATABASE_TABLE_NAME + " (id INTEGER PRIMARY KEY, "
        + COLUMN_NAME_TYPE + " TEXT,"
        + COLUMN_NAME_GRP_ID + " TEXT,"
        + COLUMN_NAME_SESSION_ID + " TEXT,"
        + COLUMN_NAME_GENERAL1 + " TEXT,"
        + COLUMN_NAME_GENERAL2 + " TEXT,"
        + COLUMN_NAME_GENERAL3 + " TEXT,"
        + COLUMN_NAME_GENERAL4 + " TEXT,"
        + COLUMN_NAME_GENERAL5 + " TEXT"
        + ")";

    /**
     * Defines for the positions (column index) of the general columns
     */
    public static final int COLUMN_POS_ID = 0;

    public static final int COLUMN_POS_TYPE = 1;

    public static final int COLUMN_POS_GRP_ID = 2;

    public static final int COLUMN_POS_SESSION_ID = 3;

    public static final int COLUMN_POS_GENERAL1 = 4;

    public static final int COLUMN_POS_GENERAL2 = 5;

    public static final int COLUMN_POS_GENERAL3 = 6;

    public static final int COLUMN_POS_GENERAL4 = 7;

    public static final int COLUMN_POS_GENERAL5 = 8;

    /**
     * Defines of the different types of jobs
     */
    public static final int JOBTYPE_ACKNOWLEDGE_LICENSE = 1;

    public static final int JOBTYPE_ACQUIRE_LICENCE = 2;

    public static final int JOBTYPE_DOWNLOAD_CONTENT = 3;

    public static final int JOBTYPE_DRM_FEEDBACK = 4;

    public static final int JOBTYPE_GET_METERING_CERTIFICATE = 5;

    public static final int JOBTYPE_JOIN_DOMAIN = 6;

    public static final int JOBTYPE_LAUNCH_LUIURL_IF_FAIL = 7;

    public static final int JOBTYPE_LEAVE_DOMAIN = 8;

    public static final int JOBTYPE_PROCESS_METERING_DATA = 9;

    public static final int JOBTYPE_RENEW_RIGHTS = 10;

    public static final int JOBTYPE_WEB_INITIATOR = 11;

    /**
     * Defines of the columns when it's a JOBTYPE_ACKNOWLEDGE_LICENSE
     */
    public static final int COLUMN_ACK_URL = COLUMN_POS_GENERAL1;

    public static final int COLUMN_ACK_CHALLENGE = COLUMN_POS_GENERAL2;

    /**
     * Defines of the columns when it's a JOBTYPE_ACQUIRE_LICENCE
     */
    public static final int COLUMN_ACQ_URI = COLUMN_POS_GENERAL1;

    public static final int COLUMN_ACQ_HEADER = COLUMN_POS_GENERAL2;

    public static final int COLUMN_ACQ_CUSTOMDATA = COLUMN_POS_GENERAL3;

    /**
     * Defines of the columns when it's a JOBTYPE_DOWNLOAD_CONTENT
     */
    public static final int COLUMN_DWLD_URL = COLUMN_POS_GENERAL1;

    /**
     * Defines of the columns when it's a JOBTYPE_DRM_FEEDBACK
     */
    public static final int COLUMN_DRMFEEDBACK_URI = COLUMN_POS_GENERAL1;

    public static final int COLUMN_DRMFEEDBACK_TYPE = COLUMN_POS_GENERAL2;

    public static final int COLUMN_DRMFEEDBACK_GROUP_TYPE = COLUMN_POS_GENERAL3;

    /**
     * Defines of the columns when it's a JOBTYPE_GET_METERING_CERTIFICATE
     */
    public static final int COLUMN_GETMETERING_CERT_SERVER = COLUMN_POS_GENERAL1;

    public static final int COLUMN_GETMETERING_METERING_ID = COLUMN_POS_GENERAL2;

    public static final int COLUMN_GETMETERING_CUSTOM_DATA = COLUMN_POS_GENERAL3;

    /**
     * Defines of the columns when it's a JOBTYPE_JOINDOMAIN
     */
    public static final int COLUMN_JOIN_DOMAIN_CONTROLLER = COLUMN_POS_GENERAL1;

    public static final int COLUMN_JOIN_DOMAIN_SERVICE_ID = COLUMN_POS_GENERAL2;

    public static final int COLUMN_JOIN_DOMAIN_ACCOUNT_ID = COLUMN_POS_GENERAL3;

    public static final int COLUMN_JOIN_DOMAIN_REVISION = COLUMN_POS_GENERAL4;

    public static final int COLUMN_JOIN_DOMAIN_CUSTOM_DATA = COLUMN_POS_GENERAL5;

    /**
     * Defines of the columns when it's a JOBTYPE_LAUNCH_LUIURL_IF_FAIL
     */
    public static final int COLUMN_LAUNCH_LUI_URL = COLUMN_POS_GENERAL1;

    /**
     * Defines of the columns when it's a JOBTYPE_LEAVE_DOMAIN
     */
    public static final int COLUMN_LEAVE_DOMAIN_CONTROLLER = COLUMN_POS_GENERAL1;

    public static final int COLUMN_LEAVE_DOMAIN_SERVICE_ID = COLUMN_POS_GENERAL2;

    public static final int COLUMN_LEAVE_DOMAIN_ACCOUNT_ID = COLUMN_POS_GENERAL3;

    public static final int COLUMN_LEAVE_DOMAIN_REVISION = COLUMN_POS_GENERAL4;

    public static final int COLUMN_LEAVE_DOMAIN_CUSTOM_DATA = COLUMN_POS_GENERAL5;

    /**
     * Defines of the columns when it's a JOBTYPE_PROCESS_METERING_DATA
     */
    public static final int COLUMN_PROCESS_METERING_DATA_CERTIFICATE_SERVER = COLUMN_POS_GENERAL1;

    public static final int COLUMN_PROCESS_METERING_DATA_METERING_ID = COLUMN_POS_GENERAL2;

    public static final int COLUMN_PROCESS_METERING_DATA_CUSTOM_DATA = COLUMN_POS_GENERAL3;

    public static final int COLUMN_PROCESS_METERING_DATA_MAX_PACKETS = COLUMN_POS_GENERAL4;

    public static final int COLUMN_PROCESS_METERING_DATA_ALLOWED_TO_FETCH_CERT =
        COLUMN_POS_GENERAL5;

    /**
     * Defines of the columns when it's a JOBTYPE_RENEW_RIGHTS
     */
    public static final int COLUMN_RENEW_RIGHTS_URI = COLUMN_POS_GENERAL1;

    /**
     * Defines of the columns when it's a JOBTYPE_WEB_INITIATOR
     */
    public static final int COLUMN_WEB_INITIATOR_URI = COLUMN_POS_GENERAL1;
}
