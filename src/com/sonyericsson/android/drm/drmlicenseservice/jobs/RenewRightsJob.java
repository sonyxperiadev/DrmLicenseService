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


package com.sonyericsson.android.drm.drmlicenseservice.jobs;

import com.sonyericsson.android.drm.drmlicenseservice.Constants;
import com.sonyericsson.android.drm.drmlicenseservice.DatabaseConstants;
import com.sonyericsson.android.drm.drmlicenseservice.DrmJobDatabase;
import com.sonyericsson.android.drm.drmlicenseservice.HttpClient;
import com.sonyericsson.android.drm.drmlicenseservice.HttpClient.DataHandlerCallback;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;
import android.net.Uri;
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 Start*/
import android.util.Log;
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 End*/
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class RenewRightsJob extends StackableJob {
    private Uri mFileUri = null;

    private String mLA_URL = null;

    private String mLUI_URL = null;

    public RenewRightsJob(Uri mFileUri) {
        this.mFileUri = mFileUri;
    }

    @Override
    public boolean executeNormal() {
        boolean isOk = false;
        String scheme = null;
        String tempFile = null;
        final StringBuffer headerString = new StringBuffer();
        if (mFileUri != null) {
            scheme = mFileUri.getScheme();
        }
/*SHARP_EXTEND for PlayReady MOD [mms Support] 2012.04.04 Start*/
        // for mms scheme
        if (mFileUri != null && ("http".equals(scheme) || "mms".equals(scheme))) {
/*SHARP_EXTEND for PlayReady MOD [mms Support] 2012.04.04 End*/
            tempFile = getUniqueTempFileName(mJobManager.getContext(),
                    mFileUri.getLastPathSegment());
            if (tempFile != null) {
                String mime = Constants.DRM_DLS_PIFF_MIME;
                if (tempFile.toString().endsWith(".pyv") || tempFile.toString().endsWith(".pya")) {
                    mime = Constants.DRM_DLS_MIME;
                }
                final String callbackMime = mime;
                final String callbackFile = tempFile;

                DataHandlerCallback callback = new DataHandlerCallback() {
                    public boolean handleData(byte[] buffer, int length) {
                        try {
                            FileOutputStream fos = null;
                            Context context = mJobManager.getContext();
                            File directory = context.getExternalFilesDir(null);
                            if (directory != null) {
                                fos = new FileOutputStream(callbackFile, true);
                            } else {
                                // External storage is not available, use the
                                // same path as returned by context.getFilesDir()
                                fos = context.openFileOutput(callbackFile.replaceAll(".*/", ""),
                                        Context.MODE_WORLD_READABLE | Context.MODE_APPEND);
                            }
                            try {
                                fos.write(buffer, 0, length);
                            } finally {
                                fos.close();
                            }
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        DrmInfoRequest request = new DrmInfoRequest(
                                DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO, callbackMime);
                        request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GET_DRM_HEADER);
                        request.put(Constants.DRM_DATA, callbackFile);
                        // Send message to engine to get the playready header
                        DrmInfo reply = sendInfoRequest(request);
                        if (reply != null) {
                            String replyStatus = (String)reply.get(Constants.DRM_STATUS);
                            if (replyStatus != null && replyStatus.equals("ok")) {
                                String header = (String)reply.get(Constants.DRM_DATA);
                                if (header != null && header.length() > 0) {
                                    // We have the header so stop the download
                                    headerString.append(header);
                                    return false;
                                }
                            }
                        }
                        return true;
                    }
                };
                HttpClient.Response response = HttpClient.get(mJobManager.getContext(),
                        mJobManager.getSessionId(), mFileUri.toString(),
                        mJobManager.getParameters(), callback, mRetryCallback);

                if (response == null) {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -4);
                } else if (response.getStatus() != 200) {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR,
                            response.getStatus());
                    int innerHttpError = response.getInnerStatus();
                    if (innerHttpError != 0) {
                        mJobManager.addParameter(Constants.DRM_KEYPARAM_INNER_HTTP_ERROR,
                                innerHttpError);
                    }
                }
            }
        } else if (mFileUri != null && (scheme == null || scheme.equals("file"))) {
            File file = null;
            try {
                file = new File(URLDecoder.decode(mFileUri.getEncodedPath(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
            }
            if (file != null && file.exists()) {
                String mime = Constants.DRM_DLS_PIFF_MIME;
                if (file.toString().endsWith(".pyv") || file.toString().endsWith(".pya")) {
                    mime = Constants.DRM_DLS_MIME;
                }
                DrmInfoRequest request = new DrmInfoRequest(
                        DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO, mime);
                request.put(Constants.DRM_ACTION, Constants.DRM_ACTION_GET_DRM_HEADER);
                request.put(Constants.DRM_DATA, file.toString());
                // Send message to engine to get the license challenge
                DrmInfo reply = sendInfoRequest(request);
                if (reply != null) {
                    String replyStatus = (String)reply.get(Constants.DRM_STATUS);
                    if (replyStatus != null && replyStatus.equals("ok")) {
                        String header = (String)reply.get(Constants.DRM_DATA);
                        if (header != null && header.length() > 0) {
                            headerString.append(header);
                        }
                    }
                }
            } else {
                mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -4);
            }
        }

        if (headerString.length() > 0) {
            String header = headerString.toString();
            parseUrls(header);
            if (mLA_URL != null && mLA_URL.length() > 0) {
                mJobManager.pushJob(new DrmFeedbackJob(Constants.PROGRESS_TYPE_RENEW_RIGHTS,
                        mFileUri));
                if (mLUI_URL != null && mLUI_URL.length() > 0) {
                    mJobManager.pushJob(new LaunchLuiUrlIfFailureJob(mLUI_URL));
                }
                mJobManager.pushJob(new AcquireLicenseJob(header));
                isOk = true;
            } else if (mLUI_URL != null && mLUI_URL.length() > 0) {
                if (mJobManager.getCallbackHandler() == null) {
                    // Not called via AIDL, open redirectUrl in
                    // browser
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(mLUI_URL));
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mJobManager.getContext().startActivity(i);
                    isOk = true;
                } else {
                    mJobManager.addParameter(Constants.DRM_KEYPARAM_REDIRECT_URL, mLUI_URL);
                }
            } else {
                mJobManager.addParameter(Constants.DRM_KEYPARAM_HTTP_ERROR, -5);
            }
        }
        if (tempFile != null) {
            if (!(new File(tempFile).delete())) {
                // it's OK if we couldn't delete the file
            }
        }
        if (!isOk) {
            int type = Constants.PROGRESS_TYPE_RENEW_RIGHTS;
            mJobManager.pushJob(new DrmFeedbackJob(type, mFileUri));
        }
        return isOk;
    }

    private String getUniqueTempFileName(Context context, String inputFilename) {
        String fullpath = null;
        String filename = null;
        File directory = context.getExternalFilesDir(null);
        if (directory == null) {
            directory = context.getFilesDir();
        }
        if (inputFilename == null) {
            inputFilename = "temp.ismv";
        }
        if (directory != null) {
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    // Should never fail, if it does, try to use it anyway as we
                    // need a private dir
                }
            }
            if (!new File(directory, inputFilename).exists()) {
                filename = inputFilename;
            } else {
                String name = inputFilename.replaceAll("\\.[^\\.]*", "");
                String ext = inputFilename.replaceAll(".*\\.", ".");
                long count = 0;
                do {
                    count--;
                    filename = name + count + ext;
                } while (new File(directory, filename).exists());
            }
            fullpath = directory + File.separator + filename;
        }
        return fullpath;
    }

    private void parseUrls(String header) {
        if (header != null && header.length() > 10) {
            // Prepare a reader based on our input header string
            int length = header.length();
            char c[] = new char[length];
            header.getChars(0, length, c, 0);
            Reader reader = new CharArrayReader(c);

            try {
                // Get a SAXParser from the SAXPArserFactory
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();

                // Get the XMLReader of the SAXParser we created
                XMLReader xr = sp.getXMLReader();
                // Create a new ContentHandler and apply it to the
                // XML-Reader
                XmlHandler dataHandler = new XmlHandler();
                xr.setContentHandler(dataHandler);

                // Parse the xml-data from our URL (ie Uri)
                xr.parse(new InputSource(reader));

                // Parsing is completed
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 Start*/
                String wrmHeaderVer = dataHandler.getValue(Constants.DRM_WRM_HEADER_VERSION);
                if (Constants.DRM_WRM_HEADER_VERSION_2000.equals(wrmHeaderVer)) {
                    // for WMDRM10
                    if (Constants.DEBUG) {
                        Log.d(Constants.LOGTAG, "Get WrmHeader(for WMDRM10)");
                    }
                    mLA_URL = dataHandler.getValue(Constants.DRM_LAINFO);
                    mJobManager.addParameter(Constants.DRM_LAINFO, 1);
                } else if (Constants.DRM_WRM_HEADER_VERSION_4000.equals(wrmHeaderVer)) {
                    // for PlayReady
                    mLA_URL = dataHandler.getValue("LA_URL");
                } else {
                    // for Unknown
                    if (Constants.DEBUG) {
                        Log.w(Constants.LOGTAG, "Got WrmHeader Ver. = " + wrmHeaderVer);
                    }
                }
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 End*/
                mLUI_URL = dataHandler.getValue("LUI_URL");
            } catch (ParserConfigurationException e) {
            } catch (SAXException e) {
            } catch (IOException e) {
            }
        }
    }

    private static class XmlHandler extends DefaultHandler {
        private Stack<String> mTagStack = null;

        protected HashMap<String, String> mValues = new HashMap<String, String>();

        public String getValue(String key) {
            return mValues.get(key);
        }

        @Override
        public void startDocument() throws SAXException {
            mTagStack = new Stack<String>();
            mValues = new HashMap<String, String>();
        }

        /**
         * Will be called on opening tags like: <tag> Can provide attribute(s),
         * when xml is: <tag attribute="value">
         */
        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                Attributes atts) throws SAXException {
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 Start*/
            // for WMDRM10
            // Log.d(Constants.LOGTAG, "startElement l=" + localName + " n=" + qName);
            for (int i = 0; i < atts.getLength(); i++) {
                // Log.d(Constants.LOGTAG, "attr(" + i + ") l=" + atts.getLocalName(i) + " v=" + atts.getValue(i));
                mValues.put(atts.getLocalName(i), atts.getValue(i));
            }
/*SHARP_EXTEND for PlayReady ADD [WMDRM Support] 2012.04.04 End*/
            mTagStack.push(localName);
        }

        /**
         * Will be called on closing tags like: </tag>
         */
        @Override
        public void endElement(String namespaceURI, String localName, String qName)
                throws SAXException {
            mTagStack.pop();
        }

        /**
         * Will be called on the following structure: <tag>characters</tag>
         */
        @Override
        public void characters(char ch[], int start, int length) {
            mValues.put(mTagStack.peek(), new String(ch, start, length));
        }
    }

    @Override
    public boolean writeToDB(DrmJobDatabase jobDb) {
        boolean status = true;
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_TYPE,
                DatabaseConstants.JOBTYPE_RENEW_RIGHTS);
        values.put(DatabaseConstants.COLUMN_TASKS_NAME_GRP_ID, this.getGroupId());
        if (mJobManager != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_SESSION_ID, mJobManager.getSessionId());
        }
        if (mFileUri != null) {
            values.put(DatabaseConstants.COLUMN_TASKS_NAME_GENERAL1, this.mFileUri.toString());
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
        String uriRenewString = c.getString(DatabaseConstants.COLUMN_RENEW_RIGHTS_URI);
        if (uriRenewString != null) {
            this.mFileUri = Uri.parse(uriRenewString);
        }
        this.setGroupId(c.getInt(DatabaseConstants.COLUMN_TASKS_POS_GRP_ID));
        return true;
    }
}
