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

package com.sonyericsson.android.drm.drmlicenseservice.utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Generic XML parser for any Data.
 *
 * Note: in case there are two identical tags the text will be concatenated.
 */
public class XmlParser {

    public static String parseXml(String xml, String tag) {
        String res = null;
        if (xml != null && xml.length() > 0) {
            HashMap<String,String> values = parseXmlToMap(new ByteArrayInputStream(xml.getBytes()),
                tag);
            if (values != null) {
                res = values.get(tag);
            }
        }
        return res;
    }

    public static String parseXml(File file, String tag) {
        String res = null;
        try {
            InputStream is = new FileInputStream(file);
            HashMap<String,String> values = parseXmlToMap(is, tag);
            is.close();
            if (values != null) {
                res = values.get(tag);
            }
        } catch (IOException e) {
            DrmLog.logException(e);
        }
        return res;
    }

    public static String parseXml(byte[] data, String tag) {
        String res = null;
        if (data != null) {
            InputStream is = new ByteArrayInputStream(data);
            HashMap<String,String> values = parseXmlToMap(is, tag);
            try {
                is.close();
            } catch (IOException e) {}
            if (values != null) {
                res = values.get(tag);
            }
        }
        return res;
    }

    public static HashMap<String, String> parseXml(byte[] data) {
        HashMap<String,String> res = null;
        if (data != null) {
            InputStream is = new ByteArrayInputStream(data);
            res = parseXmlToMap(is, null);
            try {
                is.close();
            } catch (IOException e) {}
        }
        return res;
    }

    public static HashMap<String, String> parseXml(String xml) {
        HashMap<String,String> res = null;
        if (xml != null && xml.length() > 0) {
            res = parseXmlToMap(new ByteArrayInputStream(xml.getBytes()), null);
        }
        return res;
    }

    private static HashMap<String, String> parseXmlToMap(InputStream is, String tag) {
        HashMap<String, String> res = new HashMap<String, String>();
        XmlPullParserFactory factory;
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(is, null); // Let XmlPullParser handle charset parsing
            int eventType = xpp.getEventType();
            StringBuffer tagBuffer = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        if (tagBuffer == null) {
                            tagBuffer = new StringBuffer();
                        }
                        tagBuffer.append(xpp.getText());
                        break;
                    case XmlPullParser.END_TAG:
                        if (tagBuffer != null) {
                            res.put(xpp.getName(), tagBuffer.toString().trim());
                            tagBuffer = null;
                            if (tag != null && xpp.getName().toLowerCase().equals(
                                    tag.toLowerCase())) {
                                DrmLog.debug("found tag, return");
                                return res;
                            }
                        }
                        break;
                    default:
                        // do nothing
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException e) {
            DrmLog.debug("res size " + res.size());
            DrmLog.logException(e);
            res = null; // parsing failed, clear result even if some tags was parsed.
        } catch (IOException e) {
            DrmLog.logException(e);
            res = null;
        }
        return res;
    }

    public static ArrayDeque<HashMap<String, String>> parseWebInitiator(byte[] data) {
        ArrayDeque<HashMap<String, String>> res = new ArrayDeque<HashMap<String, String>>();
        HashMap<String, String> item = null;
        StringBuffer headerBuffer = null;
        StringBuffer tagBuffer = null;
        XmlPullParserFactory factory;
        String currentNS = null, currentNsLevel = null;
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(new ByteArrayInputStream(data), null);
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (xpp.getDepth() == 2) {
                            item = new HashMap<String, String>();
                            item.put("type", xpp.getName());
                        } else if ("Header".equals(xpp.getName())) {
                            headerBuffer = new StringBuffer();
                        } else if (headerBuffer != null) {
                            headerBuffer.append("<").append(xpp.getName());
                            if (xpp.getNamespace() != null
                                    && !xpp.getNamespace().equals(currentNS)) {
                                currentNS = xpp.getNamespace();
                                headerBuffer.append(" xmlns=\"").append(currentNS).append('\"');
                                currentNsLevel = xpp.getName();
                            }
                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                String attr = xpp.getAttributeName(i);
                                String value = xpp.getAttributeValue(i);
                                if (attr != null && value != null) {
                                    headerBuffer.append(' ').append(attr).append("=\"");
                                    headerBuffer.append(value).append('\"');
                                }
                            }
                            headerBuffer.append('>');
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (tagBuffer == null) {
                            tagBuffer = new StringBuffer();
                        }
                        tagBuffer.append(xpp.getText());
                        if (headerBuffer != null) {
                            headerBuffer.append(xpp.getText());
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (tagBuffer != null && item != null) {
                            item.put(xpp.getName(), tagBuffer.toString().trim());
                            tagBuffer = null;
                        }
                        if ("Header".equals(xpp.getName()) && item != null &&
                                headerBuffer != null) {
                            // Apply XML character-entity encoding to previously unescaped '&'
                            item.put("Header", headerBuffer.toString().replaceAll("&(?!amp;)",
                                    "&amp;").trim());
                            headerBuffer = null;
                        } else if (headerBuffer != null) {
                            headerBuffer.append("</").append(xpp.getName()).append(">");
                        }
                        if (xpp.getName().equals(currentNsLevel)) {
                            currentNS = null;
                        }
                        if (xpp.getDepth() == 2) {
                            if (item != null) {
                                res.add(item);
                                item = null;
                            }
                        }
                        break;
                    default:
                        // do nothing
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException e) {
            DrmLog.logException(e);
            res = null; // parsing failed, clear result even if some tags was parsed.
        } catch (IOException e) {
            DrmLog.logException(e);
            res = null;
        }
        return res;
    }

    public static boolean isValidXml(String mHeader) {
        DrmLog.debug("isXmlValid " + mHeader);
        return parseXml(mHeader) != null;
    }

}
