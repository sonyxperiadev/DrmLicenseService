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
* Contributor(s):Sharp Corporation
* Portions created by Sharp Corporation are Copyright (C) 2012 Sharp 
* Corporation. All Rights Reserved.
*
* ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice;

/**
 * Constants.
 */
public class Constants {

    /** Debug log tags */
    public static final String LOGTAG = "DrmLicenseService";

    /** Debug flag */
    public static final boolean DEBUG = false;

    /** User Agent to use if no UA is provided nor possible to get from system **/
    public static final String FALLBACK_USER_AGENT = "DrmLicenseService";

    /** Intent action that starts service renewal flow */
    public static final String INTENT_ACTION_DRM_SERVICE_RENEW =
            "com.sonyericsson.intent.action.RENEW_LICENSE";

    public static final String INTENT_ACTION_DRM_SERVICE_HANDLE_WEB_INITIATOR =
            "com.sonyericsson.intent.action.WEB_INITIATOR";

    /** Intent action for renewal of rights */
    public static final String DRM_RENEW_RIGHTS_ACTION =
            "com.sonyericsson.android.drm.GET_RIGHTS";

    /** Intent action for handling Web Initiator */
    public static final String DRM_WEB_INITIATOR_ACTION =
            "com.sonyericsson.android.drm.WEB_INITIATOR";

    /** The PlayReady content MIME types */
    public static final String DRM_DLS_MIME = "audio/vnd.ms-playready.media.pya";

    public static final String DRM_DLS_PIFF_MIME = "audio/isma";

    /** The PlayReady initiator MIME types */
    public static final String DRM_DLS_INITIATOR_MIME =
            "application/" + "vnd.ms-playready.initiator+xml";

    /** Actions */
    public static final String DRM_ACTION_GET_DRM_HEADER = "GetDrmHeader";

    public static final String DRM_ACTION_GENERATE_LIC_CHALLENGE = "GenerateLicChallenge";

    public static final String DRM_ACTION_PROCESS_LIC_RESPONSE = "ProcessLicResponse";

    public static final String DRM_ACTION_PROCESS_LIC_ACK_RESPONSE = "ProcessLicAckResponse";

    public static final String DRM_ACTION_GENERATE_JOIN_DOM_CHALLENGE = "GenerateJoinDomChallenge";

    public static final String DRM_ACTION_PROCESS_JOIN_DOM_RESPONSE = "ProcessJoinDomResponse";

    public static final String DRM_ACTION_GENERATE_LEAVE_DOM_CHALLENGE = ""
            + "GenerateLeaveDomChallenge";

    public static final String DRM_ACTION_PROCESS_LEAVE_DOM_RESPONSE = "ProcessLeaveDomResponse";

    public static final String DRM_ACTION_GENERATE_METER_CERT_CHALLENGE =
        "GenerateMeterCertChallenge";

    public static final String DRM_ACTION_PROCESS_METER_CERT_RESPONSE = "ProcessMeterCertResponse";

    public static final String DRM_ACTION_GENERATE_METER_DATA_CHALLENGE =
        "GenerateMeterDataChallenge";

    public static final String DRM_ACTION_PROCESS_METER_DATA_RESPONSE = "ProcessMeterDataResponse";

    public static final String DRM_RENEW_FILE_PATH = "path";

    public static final String DRM_RENEW_RESULT_CHALLENGE = "challenge";

    public static final String DRM_RENEW_RESULT_URL = "url";

    public static final String DRM_ACTION = "Action";

    public static final String DRM_STATUS = "Status";

    public static final String DRM_HEADER = "Header";

    public static final String DRM_LA_URL = "LA_URL";
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 Start*/
    public static final String DRM_LAINFO = "LAINFO";   // for WMDRM10

    public static final String DRM_WRM_HEADER_VERSION = "version";  // for WMDRM10

    public static final String DRM_WRM_HEADER_VERSION_2000 = "2.0.0.0"; // for WMDRM10

    public static final String DRM_WRM_HEADER_VERSION_4000 = "4.0.0.0"; // for WMDRM10
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 End*/
    public static final String DRM_DATA = "Data";

    public static final String DRM_CUSTOM_DATA = "CustomData";

    public static final String DRM_DOMAIN_SERVICE_ID = "DS_ID";

    public static final String DRM_DOMAIN_ACCOUNT_ID = "AccountID";

    public static final String DRM_DOMAIN_REVISION = "Revision";

    public static final String DRM_DOMAIN_FRIENDLY_NAME = "FriendlyName";

    public static final String ALL_ZEROS_DRM_ID = "AAAAAAAAAAAAAAAAAAAAAA==";

    public static final String DRM_METERING_METERING_ID = "MeteringId";

    public static final String DRM_METERING_URL = "MeteringUrl";

    public static final String DRM_METERING_STATUS = "MeteringStatus";

    public static final String DRM_KEYPARAM_LASTFAIL = "LASTFAIL";

    public static final String DRM_KEYPARAM_CUSTOM_DATA = "CUSTOM_DATA";

    public static final String DRM_KEYPARAM_CUSTOM_DATA_USED = "CUSTOM_DATA_USED";

    public static final String DRM_KEYPARAM_CUSTOM_DATA_PREFIX = "CUSTOM_DATA_PREFIX";

    public static final String DRM_KEYPARAM_CUSTOM_DATA_SUFFIX = "CUSTOM_DATA_SUFFIX";

    public static final String DRM_KEYPARAM_USER_AGENT = "USER_AGENT";

    public static final String DRM_KEYPARAM_FRIENDLY_NAME = "FRIENDLY_NAME";

    public static final String DRM_KEYPARAM_HTTP_HEADERS = "HTTP_HEADERS";

    public static final String DRM_KEYPARAM_REDIRECT_LIMIT = "REDIRECT_LIMIT";

    public static final String DRM_KEYPARAM_TIME_OUT = "TIME_OUT";

    public static final String DRM_KEYPARAM_RETRY_COUNT = "RETRY_COUNT";

    public static final String DRM_KEYPARAM_WEB_INITIATOR = "WEB_INITIATOR";

    public static final String DRM_KEYPARAM_REDIRECT_URL = "REDIRECT_URL";

    public static final String DRM_KEYPARAM_GROUP_COUNT = "GROUP_COUNT";

    public static final String DRM_KEYPARAM_GROUP_NUMBER = "GROUP_NUMBER";

    public static final String DRM_KEYPARAM_CONTENT_URL = "CONTENT_URL";

    public static final String DRM_KEYPARAM_TYPE = "TYPE";

    public static final String DRM_KEYPARAM_FILEPATH = "FILEPATH";

    public static final String DRM_KEYPARAM_HTTP_ERROR = "HTTP_ERROR";

    public static final String DRM_KEYPARAM_INNER_HTTP_ERROR = "INNER_HTTP_ERROR";

    public static final String DRM_KEYPARAM_URL = "URL";

    public static final int PROGRESS_TYPE_WEBINI_COUNT = 1;

    public static final int PROGRESS_TYPE_FINISHED_JOB = 2;

    public static final int PROGRESS_TYPE_FINISHED_WEBINI = 3;

    public static final int PROGRESS_TYPE_CANCELLED = 4;

    public static final int PROGRESS_TYPE_RENEW_RIGHTS = 5;

    public static final int PROGRESS_TYPE_HTTP_RETRYING = 6;
/*SHARP_EXTEND for PlayReady ADD [mms Support] 2012.04.04 Start*/
    public static final String DRM_USER_AGENT = "User-Agent";   // for mms scheme

    public static final String DRM_NSPLAYER = "NSPlayer/10.0.0.3646";   // for mms scheme
/*SHARP_EXTEND for PlayReady ADD [mms Support] 2012.04.04 End*/
}
