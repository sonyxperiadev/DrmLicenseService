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

/**
 * Constants.
 */
public class Constants {

    /** Debug log tags */
    public static final String LOGTAG = "DrmLicenseService";

    /** Debug flag */
    public static final boolean DEBUG = false;

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

}
