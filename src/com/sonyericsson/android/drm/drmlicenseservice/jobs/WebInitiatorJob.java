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
 * Portions created by Sony Mobile Communications AB are Copyright (C) 2012-2013
 * Sony Mobile Communications AB. All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */

package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.Constants;
import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.HttpClient;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;
import com.sonyericsson.android.drm.drmlicenseservice.HttpClient.Response;
import com.sonyericsson.android.drm.drmlicenseservice.ServiceUtility;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.drm.DrmInfoEvent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class WebInitiatorJob extends StackableJob {
    private Uri mUri = null;

    public WebInitiatorJob(Uri mUri) {
        this.mUri = mUri;
    }

    @Override
    public boolean executeNormal() {
        boolean status = false;
        String host;
        Uri uriToRemove = null;

        if (mJobManager != null && mUri != null) {
            String respData = null;
            try {
                if (("http".equals(mUri.getScheme()) || "https".equals(mUri.getScheme()))
                        && ((host = mUri.getHost()) != null) && host.length() > 0) {
                    Response response = HttpClient.get(mJobManager.getContext(),
                            mJobManager.getSessionId(), mUri.toString(),
                            mJobManager.getParameters(), mRetryCallback);
                    if (response != null && response.getStatus() == 200) {
                        respData = response.getData();
                        if (respData == null || respData.length() == 0) {
                            if (Constants.DEBUG) {
                                Log.d(Constants.LOGTAG, "Request to " + mUri.toString()
                                        + " did not return any data.");
                            }
                            if (mJobManager != null) {
                                mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
                            }
                        }
                    } else {
                        if (Constants.DEBUG) {
                            Log.d(Constants.LOGTAG, "Request to " + mUri.toString() + " failed.");
                        }
                        if (mJobManager != null && response != null) {
                            mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR,
                                    response.getStatus());
                            int innerHttpError = response.getInnerStatus();
                            if (innerHttpError != 0) {
                                mJobManager.addParameter(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR,
                                        innerHttpError);
                            }
                        }
                    }
                } else if (ContentResolver.SCHEME_FILE.equals(mUri.getScheme()) ||
                            ContentResolver.SCHEME_CONTENT.equals(mUri.getScheme())) {
                    // This will only happen if file is loaded from Download
                    // List
                    String path = "";
                    if (ContentResolver.SCHEME_CONTENT.equals(mUri.getScheme())) {
                        String[] projection = new String[] {MediaStore.MediaColumns.DATA};
                        Cursor cursor = null;
                        try {
                            cursor = mJobManager.getContext().getContentResolver().query(
                                    mUri, projection, null, null, null);
                            if (null == cursor || 0 == cursor.getCount() || !cursor.moveToFirst()) {
                                throw new IllegalArgumentException("Given Uri could not be found" +
                                        " in media store");
                            }
                            int pathIndex =
                                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                            path = cursor.getString(pathIndex);
                        } catch (SQLiteException e) {
                            throw new IllegalArgumentException("Given Uri is not formatted in a " +
                                    "way so that it can be found in media store.");
                        } finally {
                            if (null != cursor) {
                                cursor.close();
                            }
                        }
                    } else {
                        // In case scheme equals SCHEME_FILE
                        path = mUri.getPath();
                    }
                    FileInputStream fis = new FileInputStream(path);
                    BufferedInputStream bis = new BufferedInputStream(fis);
                    StringBuffer buf = new StringBuffer();
                    while (bis.available() > 0) {
                        byte data[] = new byte[1024];
                        int count = bis.read(data);
                        buf.append(new String(data, 0, count));
                    }
                    respData = buf.toString();
                    bis.close();
                    fis.close();
                    uriToRemove = mUri;
                }

                Reader reader = null;
                if (respData != null && respData.length() > 0) {
                    // Prepare a reader based on our input xml string
                    int length = respData.length();
                    char c[] = new char[length];
                    respData.getChars(0, length, c, 0);
                    reader = new CharArrayReader(c);

                    // Get a SAXParser from the SAXPArserFactory
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                    SAXParser sp = spf.newSAXParser();

                    // Get the XMLReader of the SAXParser we created
                    XMLReader xr = sp.getXMLReader();
                    // Create a new ContentHandler and apply it to the
                    // XML-Reader
                    WebInitiatorHandler triggerHandler = new WebInitiatorHandler();
                    xr.setContentHandler(triggerHandler);

                    // Parse the xml-data from our URL (ie Uri)
                    xr.parse(new InputSource(reader));

                    // Parsing is completed
                    ArrayDeque<InitiatorDataItem> dataAll = triggerHandler.getData();

                    if (dataAll != null) {
                        // Loop through the parts of the initiator (it may
                        // be multiple parts).
                        // Starting with the last one to get the items on
                        // the job stack
                        // in the right order.
                        boolean sendLicReqStartMsg = false;
                        mJobManager.setMaxJobGroups(dataAll.size());
                        while (dataAll.size() > 0) {
                            InitiatorDataItem item = dataAll.removeLast();
                            HashMap<String, String> data = item.data;
                            String type = item.type;

                            if (type.equals("LicenseAcquisition")) {
                                String kid = data.get("LicenseAcquisition.KID");
                                String content = data.get("LicenseAcquisition.Content");
                                String header = data.get("LicenseAcquisition.Header");
                                String customData = data.get("LicenseAcquisition.CustomData");
                                String luiUrl = data.get("LicenseAcquisition.LUI_URL");
                                if (header != null && header.length() > 0 && kid != null
                                        && kid.length() > 0) {
                                    if (content != null && content.length() > 0) {
                                        mJobManager.pushJob(new DrmFeedbackJob(
                                                Constants.PROGRESS_TYPE_FINISHED_JOB,
                                                "AcquireLicense", Uri.parse(content)));
                                        mJobManager.pushJob(new DownloadContentJob(content));
                                    } else {
                                        mJobManager.pushJob(new DrmFeedbackJob(
                                                Constants.PROGRESS_TYPE_FINISHED_JOB,
                                                "AcquireLicense"));
                                    }
                                    if (luiUrl != null) {
                                        mJobManager
                                                .pushJob(new LaunchLuiUrlIfFailureJob(luiUrl));
                                    }
                                    mJobManager.pushJob(new AcquireLicenseJob(header,
                                            customData));
                                    sendLicReqStartMsg = true;
                                } else {
                                    if (Constants.DEBUG) {
                                        Log.d(Constants.LOGTAG,
                                                "Missing or incorrect Header/Kid or LUI_URL");
                                    }
                                }
                            } else if (type.equals("JoinDomain")) {
                                mJobManager.pushJob(new DrmFeedbackJob(
                                        Constants.PROGRESS_TYPE_FINISHED_JOB, "JoinDomain"));
                                mJobManager.pushJob(new JoinDomainJob(data
                                        .get("JoinDomain.DomainController"), data
                                        .get("JoinDomain.DS_ID"), data
                                        .get("JoinDomain.AccountID"), data
                                        .get("JoinDomain.Revision"), data
                                        .get("JoinDomain.CustomData")));
                                sendLicReqStartMsg = true;
                            } else if (type.equals("LeaveDomain")) {
                                mJobManager.pushJob(new DrmFeedbackJob(
                                        Constants.PROGRESS_TYPE_FINISHED_JOB, "LeaveDomain"));
                                mJobManager.pushJob(new LeaveDomainJob(data
                                        .get("LeaveDomain.DomainController"), data
                                        .get("LeaveDomain.DS_ID"), data
                                        .get("LeaveDomain.AccountID"), data
                                        .get("LeaveDomain.Revision"), data
                                        .get("LeaveDomain.CustomData")));
                            } else if (type.equals("Metering")) {
                                mJobManager.pushJob(new DrmFeedbackJob(
                                        Constants.PROGRESS_TYPE_FINISHED_JOB,
                                        "GetMeteringCertificate"));
                                mJobManager.pushJob(new ProcessMeteringDataJob(data
                                        .get("Metering.CertificateServer"), data
                                        .get("Metering.MeteringID"), data
                                        .get("Metering.CustomData"), data
                                        .get("Metering.MaxPackets"), true));

                            } else {
                                mJobManager.pushJob(new DrmFeedbackJob(
                                        Constants.PROGRESS_TYPE_FINISHED_JOB,
                                        type));
                                mJobManager.pushJob(new ForceFailureJob());
                                if (Constants.DEBUG) {
                                    Log.w(Constants.LOGTAG, "Unknown initiator: " + type);
                                }
                            }
                            mJobManager.createNewJobGroup();
                        }
                        mJobManager.pushJob(new DrmFeedbackJob(
                                Constants.PROGRESS_TYPE_WEBINI_COUNT, mUri));
                        status = true;
                        if (sendLicReqStartMsg == true) {
                            ServiceUtility.sendOnInfoResult(mJobManager.getContext(),
                                    DrmInfoEvent.TYPE_WAIT_FOR_RIGHTS, mUri.getEncodedPath());
                        }
                    } else {
                        if (mJobManager != null) {
                            mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
                        }
                    }
                }
            } catch (MalformedURLException e) {
                if (mJobManager != null) {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
                }
            } catch (ParserConfigurationException e) {
                if (mJobManager != null) {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
                }
            } catch (SAXException e) {
                if (mJobManager != null) {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
                }
            } catch (IOException e) {
                if (mJobManager != null) {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
                }
            }
        } else {
            if (mJobManager != null) {
                mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
            }
        }
        if (mJobManager != null && mJobManager.hasParameter(Constants.DRM_KEYPARAM_HTTP_ERROR)) {
            // Add job to notify that download/parsing of WebInitiator failed.
            mJobManager.pushJob(new DrmFeedbackJob(Constants.PROGRESS_TYPE_WEBINI_COUNT, mUri));
        }
        if (status && uriToRemove != null) {
            new File(uriToRemove.getPath()).delete();
        }
        return status;
    }

    private static class InitiatorDataItem {
        public HashMap<String, String> data;

        public String type;

        public InitiatorDataItem() {
            data = new HashMap<String, String>();
        }
    }

    private static class WebInitiatorHandler extends DefaultHandler {
        private Stack<String> mTagStack = null;

        private StringBuffer headerBuffer = null;

        private String currentNs = null;

        private String currentNsLevel = null;

        private ArrayDeque<InitiatorDataItem> mData = null;

        private InitiatorDataItem currentItem = null;

        public ArrayDeque<InitiatorDataItem> getData() {
            return mData;
        }

        @Override
        public void startDocument() throws SAXException {
            mTagStack = new Stack<String>();
            mData = new ArrayDeque<InitiatorDataItem>();
        }

        @Override
        public void endDocument() throws SAXException {
            // Nothing to do
        }

        /**
         * Will be called on opening tags like: <tag> Can provide attribute(s),
         * when xml is: <tag attribute="value">
         */
        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                Attributes atts) throws SAXException {
            if (mTagStack.size() == 1) {
                currentItem = new InitiatorDataItem();
                currentItem.type = localName;
            }
            if (headerBuffer != null) {
                headerBuffer.append('<').append(localName);
                if (namespaceURI != null) {
                    if (currentNs == null || !currentNs.equals(namespaceURI)) {
                        headerBuffer.append(" xmlns=\"").append(namespaceURI).append('\"');
                        currentNs = namespaceURI;
                        currentNsLevel = localName;
                    }
                }
                if (atts != null) {
                    for (int i = 0; i < atts.getLength(); i++) {
                        headerBuffer.append(' ').append(atts.getLocalName(i)).append("=\"");
                        headerBuffer.append(atts.getValue(i)).append('\"');
                    }
                }
                headerBuffer.append('>');
            } else if (localName != null && localName.equals("Header")) {
                headerBuffer = new StringBuffer();
            }
            mTagStack.push(localName);
        }

        /**
         * Will be called on closing tags like: </tag>
         */
        @Override
        public void endElement(String namespaceURI, String localName, String qName)
                throws SAXException {
            mTagStack.pop();
            if (localName != null && localName.equals("Header") && headerBuffer != null) {
                currentItem.data.put(currentItem.type + "." + localName, headerBuffer.toString());
                headerBuffer = null;
            } else {
                if (headerBuffer != null) {
                    headerBuffer.append("</").append(localName).append('>');
                }
            }
            if (localName != null && localName.equals(currentNsLevel)) {
                currentNs = null;
            }
            if (mTagStack.size() == 1) {
                mData.add(currentItem);
                currentItem = null;
            }
        }

        /**
         * Will be called on the following structure: <tag>characters</tag>
         */
        @Override
        public void characters(char ch[], int start, int length) {
            if (currentItem != null) {
                String tag = currentItem.type + "." + mTagStack.peek();
                String old = currentItem.data.get(tag);
                if (old == null) {
                    old = "";
                }
                currentItem.data.put(tag, old + new String(ch, start, length));
            }
            if (headerBuffer != null) {
                headerBuffer.append(new String(ch, start, length));
            }
        }
    }

    @Override
    public boolean writeToDB(DrmJobDatabase jobDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_TYPE,
                DatabaseConstants.JOBTYPE_WEB_INITIATOR);
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        if (this.mUri != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_GENERAL1, this.mUri.toString());
        }
        long result = jobDb.insert(values);
        if (result != -1) {
            super.setDatabaseId(result);
        } else {
            status = false;
        }
        return status;
    }

    @Override
    public boolean readFromDB(Cursor c) {
        boolean status = true;
        String uriWebString = c.getString(DatabaseConstants.COLUMN_WEB_INITIATOR_URI);
        if (uriWebString != null) {
            this.mUri = Uri.parse(uriWebString);
        } else {
            this.mUri = null;
        }
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_TASKS_POS_GRP_ID));

        return status;
    }
}
