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

/**
 * IDrmLicenseServiceCallback.aidl
 */

package com.sonyericsson.android.drm.drmlicenseservice;

import android.os.Bundle;
import android.net.Uri;

interface IDrmLicenseServiceCallback {
/**
 * Progress report from Service to Client.
 *
 * @param[in] sessionId  The sessionId that the report is connected to.
 * @param[in] state      The kind of status that is reported:
 *                             1 = Web Initiator count
 *                             2 = Finished job
 *                             3 = Finished Web Initiator
 *                             4 = Canceled
 *                             5 = Renew rights completed
 * @param[in] status     If the action was considered to be successful.
 * @param[in] parameters A map containing additional parameters, examples:
 *                          state == *all*
 *                             "WEB_INITIATOR", "http://www.somewhere.com/script?param=1"
 *                             "CUSTOM_DATA", "Operator"
 *                          state == *any* (may be used in some states)
 *                             "REDIRECT_URL", "http://www.somewhere.com/NextPageToLoad"
 *                             "HTTP_ERROR", Ordinary http error codes or negative value for other
 *                                           -1 Too many retries
 *                                           -2 Http session took more than
 *                                                   (TimeOut+1)*(RetryCount+1)
 *                                           -3 Too many redirects
 *                                           -4 Internal error, bad interface usage
 *                                           -5 XML parse error while handling WebInitiator.
 *                                           -6 Unhandled error in PlayReady PortingKit on client,
 *                                                    probably bad input values.
 *                             "INNER_HTTP_ERROR", Optional ordinary http error codes if HTTP_ERROR
 *                                           is negative
 *                          state == 1
 *                             "GROUP_COUNT", * (int, total number of items in WebInitiator)
 *                          state == 2
 *                             "GROUP_COUNT", * (int, total number of items in WebInitiator)
 *                             "GROUP_NUMBER", * (int, sequence number within WebInitiator)
 *                             "CONTENT_URL", "http://www.anywhere.com/content.file"
 *                             "TYPE", "AcquireLicense"/"JoinDomain"/"LeaveDomain"/"Metering"
 *                             "CUSTOM_DATA", "Prefix Operator Suffix"
 *                          state == 5
 *                             "FILEPATH", "/sdcard/music/file.isma"
 * @return               Status of callback execution. Optional for service to consider.
 */
boolean onProgressReport(long sessionId, int state, boolean status, in Bundle parameters);

}
