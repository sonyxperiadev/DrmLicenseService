package com.sonyericsson.android.drm.drmlicenseservice;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Generic XML parser for any Data. Although we have several cases were we
 * only parse for one specific tag we parse entire XML in those cases. Could
 * be optimized to break after specific tag, but considering this is in
 * milliseconds a generic solution should be enough.
 *
 * Note: in case there are two identical tags the text will be concatenated.
 */
public class XmlParser {

    public static String parseXml(String xml, String tag) {
        String res = null;
        HashMap<String, String> values = parseXml(xml);
        if (values != null) {
            res = values.get(tag);
        }
        return res;
    }

    public static HashMap<String,String> parseXml(String xml) {
        HashMap<String, String> res = null;
        if (xml != null && xml.length() > 0) {
            res = parseXml(new StringReader(xml));
        }
        return res;
    }

    public static HashMap<String,String> parseXml(Reader reader) {
        HashMap<String, String> res = new HashMap<String, String>();
        XmlPullParserFactory factory;
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(reader);
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

    public static ArrayDeque<HashMap<String, String>> parseWebInitiator(String xml) {
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
            xpp.setInput(new StringReader(xml));
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
                        if ("Header".equals(xpp.getName())) {
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
