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

import org.apache.http.util.ByteArrayBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ErrorMessageParser {

    public ErrorData parseXML(ByteArrayBuffer xmlData) {
        if (xmlData != null && xmlData.length() > 0) {
            try {
                // Prepare a stream based on our input xml string
                ByteArrayInputStream bais = new ByteArrayInputStream(xmlData.buffer(), 0,
                        xmlData.length());

                // Get a SAXParser from the SAXPArserFactory
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();

                // Get the XMLReader of the SAXParser we created
                XMLReader xr = sp.getXMLReader();
                // Create a new ContentHandler and apply it to the XML-Reader
                ErrorXmlHandler triggerHandler = new ErrorXmlHandler();
                xr.setContentHandler(triggerHandler);

                // Parse the xml-data from our string
                xr.parse(new InputSource(bais));

                // Parsing is completed
                ErrorData data = triggerHandler.getData();

                return data;
            } catch (ParserConfigurationException e) {
                // Log.w(Constants.LOGTAG,
                // "XML parser not configured correctly: " + e.getMessage());
            } catch (SAXException e) {
                // Log.w(Constants.LOGTAG, "Error occurred in XML parsing: " +
                // e.getMessage());
            } catch (IOException e) {
                // Log.w(Constants.LOGTAG,
                // "Error occurred while reading url data: " + e.getMessage());
            }
        } else {
            // Log.w(Constants.LOGTAG, "The supplied xml data is null");
        }
        return null;
    }

    public class ErrorData {
        protected HashMap<String, String> mValues = new HashMap<String, String>();

        public String getValue(String key) {
            return mValues.get(key);
        }
    }

    protected class ErrorXmlHandler extends DefaultHandler {
        private Stack<String> mTagStack = null;

        private ErrorData mData = null;

        public ErrorData getData() {
            return mData;
        }

        @Override
        public void startDocument() throws SAXException {
            mTagStack = new Stack<String>();
            mData = new ErrorData();
        }

        /**
         * Will be called on opening tags like: <tag> Can provide attribute(s),
         * when xml is: <tag attribute="value">
         */
        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                Attributes atts) throws SAXException {
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
            String currentTag = mTagStack.peek();
            String newValue = new String(ch, start, length);
            String oldValue = mData.mValues.put(currentTag, newValue);
            if (oldValue != null) {
                mData.mValues.put(currentTag, oldValue + newValue);
            }
        }
    }
}
