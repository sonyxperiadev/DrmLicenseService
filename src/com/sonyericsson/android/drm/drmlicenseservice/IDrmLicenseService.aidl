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
 * IDrmLicenseService.aidl
 */

package com.sonyericsson.android.drm.drmlicenseservice;

import android.os.Bundle;
import android.net.Uri;
import com.sonyericsson.android.drm.drmlicenseservice.IDrmLicenseServiceCallback;

interface IDrmLicenseService {

/**
 * Start the activity of handling a PlayReady WebInitiator.
 *
 * @param[in] uri        The uri to where the web initiator is available
 * @param[in] parameters A map containing additional parameters, examples:
 *                           "USER_AGENT", "Operator/2.0 service/3.0"
 *                           "FRIENDLY_NAME", "NameOfPhone" // Used in domain protocols to
 *                                                          // identify the device
 *                           "HTTP_HEADERS" // A Bundle of headers where key+value is used in
 *                                          // in the http request
 *                           "REDIRECT_LIMIT" // Max number of http redirects to follow (def. 20)
 *                           "TIME_OUT",    // Connection timeout (in seconds), default is 30
 *                           "RETRY_COUNT", // Http request retries, default is 5
 *                           "CUSTOM_DATA", "OperatorService"  // Will replace cd from WebInitiator
 *                           "CUSTOM_DATA_PREFIX", "Operator " // Will be added before cd from WI
 *                           "CUSTOM_DATA_SUFFIX", " Operator" // Will be added after cd from WI
 * @param[in] callbackHandler The implementation of the callback interface provided by the client.
 * @return               SessionId or 0 if uri input parameters is null.
 */
long handleWebInitiator(in Uri uri,
                        in Bundle parameters,
                        IDrmLicenseServiceCallback callbackHandler);

/**
 * Renew rights for a specific file
 *
 * @param[in] filePath   Full path to the file that need new rights
 * @param[in] parameters A map containing additional parameters, examples:
 *                           "USER_AGENT", "Operator/2.0 service/3.0"
 *                           "FRIENDLY_NAME", "NameOfPhone" // Used in domain protocols to
 *                                                          // identify the device
 *                           "HTTP_HEADERS" // A Bundle of headers where key+value is used in
 *                                          // in the http request
 *                           "REDIRECT_LIMIT" // Max number of http redirects to follow (def. 20)
 *                           "TIME_OUT",    // Connection timeout (in seconds), default is 30
 *                           "RETRY_COUNT", // Http request retries, default is 5
 *                           "CUSTOM_DATA", "OperatorService"  // Will replace cd from WebInitiator
 *                           "CUSTOM_DATA_PREFIX", "Operator " // Will be added before cd from WI
 *                           "CUSTOM_DATA_SUFFIX", " Operator" // Will be added after cd from WI
 * @param[in] callbackHandler The implementation of the callback interface provided by the client.
 * @return               SessionId or 0 if file input parameters is null.
 */
long renewRights(in Uri filePath, in Bundle parameters, IDrmLicenseServiceCallback callbackHandler);

/**
 * Set the callback handler.
 * If service connection is broken before the request is completed, the application must
 * bind again to the service and call this function in order for the request to start again.
 * The service can be shutdown by android if there is low memory even though there are
 * connections open to the service.
 *
 * @param[in] sessionId to start processing again.
 * @param[in] callbackHandler The implementation of the callback interface provided by the client.
 * @param[in] parameters A map containing additional parameters,
 *                       see handleWebInitiator() for details.
 */
void setCallbackListener(IDrmLicenseServiceCallback callbackHandler,
                         long sessionId,
                         in Bundle parameters);

/**
 * Cancel an ongoing session.
 *
 * @param[in] sessionId  The sessionId returned when WebInitiator handling was started.
 * @return               Status of the cancellation, whether sessionId was found.
 */
boolean cancelSession(long sessionId);

}
