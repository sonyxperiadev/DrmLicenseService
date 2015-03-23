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

import com.sonyericsson.android.drm.drmlicenseservice.DLSHttpClient.DataHandlerCallback;
import com.sonyericsson.android.drm.drmlicenseservice.parser.DrmPiffParser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;

public class HeaderExtractor {

    public static void parseFile(Context context, String fileUri,
            RequestManager.Task task) {

        Uri uri = Uri.parse(fileUri);

        final StringBuffer headerString = new StringBuffer();

        String tempFile = null;
        String scheme = uri.getScheme();

        if ("http".equals(scheme)) {
            tempFile = getUniqueTempFileName(context, uri.getLastPathSegment());
            if (tempFile != null) {
                final boolean manifest = fileUri.toLowerCase(Locale.US).endsWith(".ism/manifest");
                final String callbackFile = tempFile;
                DataHandlerCallback callback = new DataHandlerCallback() {

                    @SuppressLint("WorldReadableFiles")
                    public void handleData(InputStream is) {
                        try {
                            FileOutputStream fos = new FileOutputStream(callbackFile, true);
                            int dataCounter = 0, read;
                            byte[] buffer = new byte[2048];
                            boolean isUTF16 = false, checkedUTF = false;
                            try {
                                while ((read = is.read(buffer)) != -1) {
                                    dataCounter += read;
                                    if (manifest && !checkedUTF && read > 50) {
                                        // only check if we have a arbitrary
                                        // read size. Parsing would probably
                                        // fail for less any way since size
                                        // 50 is not enough for manifest
                                        for (int i = 0; i < 50; i++) {
                                            if (buffer[i] == 0x00) {
                                                isUTF16 = true;
                                                break;
                                            }
                                        }
                                        checkedUTF = true;
                                    }
                                    fos.write(buffer, 0, read);

                                    String header;
                                    if (manifest) {
                                        header = findManifestHeader(callbackFile, isUTF16);
                                    } else {
                                        header = findHeader(callbackFile);
                                    }

                                    if (header != null) {
                                        // We have the header, stop download
                                        headerString.append(header);
                                        break;
                                    }

                                    if (!manifest && dataCounter > 20 * 1024) {
                                        // PR header has not been found in the
                                        // first 20kB of the file, it is
                                        // probably a non-DRM file, stop
                                        // trying to renew.
                                        break;
                                    }
                                }
                            } finally {
                                fos.close();
                            }
                        } catch (IOException e) {
                            DrmLog.logException(e);
                        }
                    }

                };

                DLSHttpClient.Response response = DLSHttpClient.get(context,
                        task.mDlsSessionId, uri.toString(), callback, null);

                if (response == null) {
                    task.mHttpError = Constants.HTTP_ERROR_INTERNAL_ERROR;
                } else if (response.getStatus() != 200) {
                    task.mHttpError = response.getStatus();
                    int innerHttpError = response.getInnerStatus();
                    if (innerHttpError != 0) {
                        task.mInnerHttpError = innerHttpError;
                    }
                }
            }
        } else if ((scheme == null || scheme.equals("file"))) {
            File file = null;
            try {
                file = new File(URLDecoder.decode(uri.getEncodedPath(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                DrmLog.logException(e);
            }
            if (file != null && file.exists()) {
                String header = findHeader(file.getAbsolutePath());
                if (header != null && header.length() > 0) {
                    headerString.append(header);
                }
            } else {
                task.mHttpError = Constants.HTTP_ERROR_INTERNAL_ERROR;
            }
        }
        if (headerString.length() > 0) {
            String header = headerString.toString();
            task.mHeader = header;
        } else if (task.mHttpError != Constants.HTTP_ERROR_INTERNAL_ERROR) {
            // The file is not a DRM file
            task.mHttpError = Constants.HTTP_ERROR_UNHANDLED_ERROR_IN_PK;
        }
        if (tempFile != null) {
            if (!(new File(tempFile).delete())) {
                // it's OK if we couldn't delete the file
            }
        }
    }

    private static String getUniqueTempFileName(Context context, String inputFilename) {
        File directory = new File(context.getCacheDir().getAbsolutePath());
        String fullpath = null;
        if (inputFilename == null || inputFilename.length() == 0) {
            inputFilename = "temp.ismv";
        }
        if (directory != null) {
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    // Should never fail, if it does, try to use it anyway as we
                    // need a private dir
                }
            }
            String filename;
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

    public static void parsePSSH(byte[] pssh, RequestManager.Task task) {
        try {
            task.mHeader = DrmPiffParser.getPlayReadyHeader(pssh);
            if (task.mHeader == null) {
                task.mHttpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
            }
        } catch (Exception e) {
            task.mHttpError = Constants.HTTP_ERROR_XML_PARSING_ERROR;
            DrmLog.logException(e);
        }
    }

    public static String findHeader(String file) {
        String header = null;
        try {
            DrmPiffParser parser = new DrmPiffParser();
            header = parser.getPlayReadyHeader(file);
        } catch (Exception e) {
            DrmLog.logException(e);
        }
        return header;
    }

    private static String findManifestHeader(String inputFilename, boolean isUTF16) {
        String header = null;
        try {
            InputStream is = new FileInputStream(new File(inputFilename));
            InputStreamReader isr = new InputStreamReader(is, isUTF16 ? "UTF-16" : "UTF-8");
            HashMap<String, String> parsedXml = XmlParser.parseXml(isr);
            isr.close();
            is.close();
            if (parsedXml != null) {
                String protectionHeader = parsedXml.get("ProtectionHeader");
                if (protectionHeader != null) {
                    try {
                        byte[] wrmheader = Base64.decode(protectionHeader, 0);
                        header = DrmPiffParser.getPlayReadyHeader(wrmheader);
                    } catch (Exception e) {
                        // Base64.decode or getPlayReadyHeader failed somewhere,
                        // probably tried to parse incomplete header
                        DrmLog.logException(e);
                    }
                }
            }
        } catch (IOException e) {
            DrmLog.logException(e);
        }
        return header;
    }
}
