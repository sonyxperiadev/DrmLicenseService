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

package com.sonyericsson.android.drm.drmlicenseservice.parser;

import com.sonyericsson.android.drm.drmlicenseservice.utils.DrmLog;

import android.util.SparseArray;

import java.io.RandomAccessFile;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.lang.Long;
import java.io.UnsupportedEncodingException;

public class DrmPiffParser {
    private static final int DRM_PLAYREADY_OBJECTS_HEADER_RECORD_COUNT_OFFSET = 4;
    private static final int DRM_PLAYREADY_OBJECTS_HEADER_RECORD_OFFSET = 6;
    private static final int DRM_PLAYREADY_RECORD_LENGTH_OFFSET = 2;
    private static final int DRM_PLAYREADY_RECORD_VALUE_OFFSET = 4;
    private static final int DRM_PLAYREADY_RECORD_MIN_LENGTH = 4;

    public static final int BOX_TYPE_UUID = ('u' << 24 | 'u' << 16 | 'i' << 8 | 'd');
    private static final int PIFF_BRAND = ('p' << 24 | 'i' << 16 | 'f' << 8 | 'f');
    private static final int PIFF_MINOR_VERSION = 0x00000001;
    private static final long MAX_BOX_SIZE = 1000000;

    private Uuid playReadySystemId = new Uuid(new byte[] {
            (byte)0x9A, (byte)0x04, (byte)0xF0, (byte)0x79, (byte)0x98, (byte)0x40, (byte)0x42,
            (byte)0x86, (byte)0xAB, (byte)0x92, (byte)0xE6, (byte)0x5B, (byte)0xE0, (byte)0x88,
            (byte)0x5F, (byte)0x95
    });

    private Uuid uuidProtSysSpecificHeaderBox = new Uuid(new byte[] {
            (byte)0xD0, (byte)0x8A, (byte)0x4F, (byte)0x18, (byte)0x10, (byte)0xF3, (byte)0x4A,
            (byte)0x82, (byte)0xB6, (byte)0xC8, (byte)0x32, (byte)0xD8, (byte)0xAB, (byte)0xA1,
            (byte)0x83, (byte)0xD3
    });

    private BoxType ftypBoxType = new BoxType("ftyp");
    private BoxType moovBoxType = new BoxType("moov");
    private BoxType psshBoxType = new BoxType("pssh");

    private BoxType protSysSpecificHeaderBoxType = new BoxType(uuidProtSysSpecificHeaderBox);

    private RandomAccessFile mFile;
    private boolean mExtendedParsing;
    private long mOffset;
    private SparseArray<Box> mFileStructure = new SparseArray<Box>();

    /**
     *  Creates a parser for piff files.
     */
    public DrmPiffParser() {
        DrmLog.debug("start");
        mExtendedParsing = false;
        mOffset = 0;
        DrmLog.debug("end");
    }

    /**
     * Extracts pssh box from file header
     *
     * @param path
     * @return pssh data, or null if not found
     */
    public byte[] getPsshBox(String path) throws Exception {
        DrmLog.debug("start");
        parseFile(path);
        DrmLog.debug("end");
        return getPlayReadyObjects();
    }

    /**
     * Parses file for pssh data, and if found tries to extract header from that
     * data.
     *
     *
     * @param path
     * @return header, or null if not found
     */
    public String getPlayReadyHeader(String path) throws Exception {
        DrmLog.debug("start");
        parseFile(path);
        byte[] playReadyObjects = getPlayReadyObjects();
        DrmLog.debug("end");
        return getPlayReadyHeader(playReadyObjects);
    }

    /**
     * Extract PlayReady header from pssh data.
     *
     * @param playReadyObjects
     * @return header, or null if not found
     */
    public static String getPlayReadyHeader(byte[] playReadyObjects) {
        DrmLog.debug("start");
        String result = null;
        if (playReadyObjects != null) {
            long recordOffset = 0;
            int playReadyObjectSize = 0;
            int numberOfRecords = 0;

            playReadyObjectSize = Helper.Uint32FromBufferSmall(playReadyObjects, 0);
            numberOfRecords = Helper.Uint16FromBufferSmall(playReadyObjects,
                    DRM_PLAYREADY_OBJECTS_HEADER_RECORD_COUNT_OFFSET);
            recordOffset = DRM_PLAYREADY_OBJECTS_HEADER_RECORD_OFFSET; // offset to first record

            // Read a PlayReady Record
            while (recordOffset <= playReadyObjectSize - DRM_PLAYREADY_RECORD_VALUE_OFFSET) {
                int recordType = Helper.Uint16FromBufferSmall(playReadyObjects, (int)recordOffset);
                int recordValueSize = Helper.Uint16FromBufferSmall(playReadyObjects,
                        (int)recordOffset + DRM_PLAYREADY_RECORD_LENGTH_OFFSET);

                if (recordValueSize < DRM_PLAYREADY_RECORD_MIN_LENGTH) {
                    DrmLog.debug("Playready record to small");
                    break;
                }

                long recordValueOffset = recordOffset + DRM_PLAYREADY_RECORD_VALUE_OFFSET;

                if (1 == recordType
                        && recordValueOffset + recordValueSize <= playReadyObjects.length) {
                    // header found
                    try {
                        result = new String(playReadyObjects, (int)recordValueOffset,
                                recordValueSize, "UTF-16LE");
                    } catch (UnsupportedEncodingException ex) {
                        result = null;
                    }

                    break;
                } else {
                    // Step to next record
                    if (--numberOfRecords == 0) {
                        DrmLog.debug("No more playready records");
                        break;
                    }
                    recordOffset = recordOffset + DRM_PLAYREADY_RECORD_VALUE_OFFSET
                            + recordValueSize;
                }
            }
        }
        DrmLog.debug("end");
        return result;
    }

    private boolean isPiffFile(Boxes.FileTypeBox box) {
        DrmLog.debug("start");
        if (box.majorBrand == PIFF_BRAND) {
            DrmLog.debug("end");
            return box.minorVersion == PIFF_MINOR_VERSION;
        }
        for (int i = 0; i < box.compatibleBrands.size(); ++i) {
            if (box.compatibleBrands.get(i) == PIFF_BRAND) {
                DrmLog.debug("end");
                return true;
            }
        }
        DrmLog.debug("end");
        return false;
    }

    private boolean readBytes(byte[] buffer, int numBytes) {
        DrmLog.debug("start");
        long readed = 0;

        try {
            readed = mFile.read(buffer, 0, numBytes);
            DrmLog.debug("end");
            return readed == numBytes;
        } catch (IOException ex) {
            DrmLog.error("IOException");
            return false;
        }
    }

    private boolean setFilePos(long offset) {
        DrmLog.debug("start");
        try {
            mFile.seek(offset);
            DrmLog.debug("end");
            return true;
        } catch (IOException ex) {
            DrmLog.error("IOException");
            return false;
        }
    }

    private long setFilePos() {
        DrmLog.debug("start");
        try {
            DrmLog.debug("end");
            return mFile.length();
        } catch (IOException ex) {
            DrmLog.error("IOException");
            return -1;
        }
    }

    private Box createFileTypeBox(long offset, long size, BoxType boxType) {
        DrmLog.debug("start");
        Box box = null;
        Boxes.FileTypeBox derivedBox = new Boxes.FileTypeBox(offset, size, boxType);
        int bufferSize = (int)(derivedBox.end() - mOffset);

        if (bufferSize >= 8) {
            byte[] buffer = new byte[bufferSize];

            if (readBytes(buffer, bufferSize)) {
                mOffset += bufferSize;
                derivedBox.majorBrand = Helper.Uint32FromBuffer(buffer, 0);
                derivedBox.minorVersion = Helper.Uint32FromBuffer(buffer, 4);

                for (int i = 8; i < bufferSize; i += 4) {
                    derivedBox.compatibleBrands.add(Helper.Uint32FromBuffer(buffer, i));
                }

                if (isPiffFile(derivedBox)) {
                    box = derivedBox;
                }
            }
        }
        DrmLog.debug("end");
        return box;
    }

    private Box createProtSysSpecificHeaderBox(long offset, long size, BoxType boxType) {
        DrmLog.debug("start");
        Box box = null;
        Boxes.ProtSysSpecificHeaderBox derivedBox = new Boxes.ProtSysSpecificHeaderBox(offset,
                size, boxType);

        byte[] buffer = new byte[24];
        if (readBytes(buffer, buffer.length)) {
            mOffset += buffer.length;
            derivedBox.systemId = new Uuid(buffer, 4, Uuid.UUID_LEN);
            derivedBox.dataSize = Helper.Uint32FromBuffer(buffer, 20);
            if (mOffset + derivedBox.dataSize == derivedBox.end()) {
                derivedBox.data = new byte[derivedBox.dataSize];

                if (readBytes(derivedBox.data, derivedBox.dataSize)) {
                    mOffset += derivedBox.dataSize;
                    box = derivedBox;
                }
            }
        }
        DrmLog.debug("end");
        return box;
    }

    private Box createBox(long offset, long size, BoxType boxType) {
        DrmLog.debug("start");
        Box box = null;
        if (boxType.equals(moovBoxType)) {
            box = new Box(offset, size, boxType);
        } else if (boxType.equals(ftypBoxType)) {
            box = createFileTypeBox(offset, size, boxType);
        } else if (boxType.equals(psshBoxType) || boxType.equals(protSysSpecificHeaderBoxType)) {
            box = createProtSysSpecificHeaderBox(offset, size, boxType);
        } else {
            if (size > 0) {
                long filePos = offset + size;
                if (setFilePos(filePos)) {
                    mOffset = filePos;
                    box = new Box(offset, size, boxType);
                }
            } else {
                long fileSize;
                fileSize = setFilePos();
                if (fileSize != -1) {
                    mOffset = fileSize;
                    size = mOffset - offset;
                    box = new Box(offset, size, boxType);
                }
            }
        }
        DrmLog.debug("end");
        return box;
    }

    private boolean isAsciiPrintable(byte ch) {
        DrmLog.debug("start");
        return ch >= 32 && ch < 127;
    }

    private Box readBox(long maxSize) {
        DrmLog.debug("start");
        long offset = mOffset;
        byte[] buffer = new byte[8];
        if (!readBytes(buffer, buffer.length)) {
            DrmLog.debug("end");
            return null;
        }
        if (!isAsciiPrintable(buffer[4]) || !isAsciiPrintable(buffer[5])
                || !isAsciiPrintable(buffer[6]) || !isAsciiPrintable(buffer[7])) {
            DrmLog.debug("end");
            return null;
        }
        mOffset += buffer.length;
        long size = Helper.Uint32FromBuffer(buffer, 0);

        BoxType boxType = new BoxType(buffer, 4);

        if (size == 1) {
            if (!readBytes(buffer, buffer.length)) {
                DrmLog.debug("end");
                return null;
            }
            mOffset += buffer.length;
            size = Helper.Uint64FromBuffer(buffer, 0);
        }
        if (boxType.type == BOX_TYPE_UUID) {
            boxType.uuid = new Uuid();
            if (boxType.uuid == null || !readBytes(boxType.uuid.value, Uuid.UUID_LEN)) {
                DrmLog.debug("end");
                return null;
            }
            mOffset += Uuid.UUID_LEN;
        }
        // to avoid OutOfMemory in corrupt files we check that box size is not
        // greater than file size, and in case of URI renew we need to consider
        // file size less than box size
        if (size > 0 && offset + size < mOffset || size > ((maxSize < MAX_BOX_SIZE) ? MAX_BOX_SIZE
                : maxSize)) {
            DrmLog.debug("end");
            return null;
        }

        Box b = createBox(offset, size, boxType);
        DrmLog.debug("end");
        return b;
    }

    private void parseFile(String path) {
        DrmLog.debug("start");
        try {
            mFile = new RandomAccessFile(path, "r");
            long fileSize = mFile.length();
            Box box = readBox(fileSize);
            if (box != null) {
                if (!box.boxType.equals(ftypBoxType)) {
                } else {
                    List<Box> stack = new ArrayList<Box>();

                    long limitedParsingEnd = Long.MAX_VALUE;
                    do {
                        while (stack.size() > 0 && box.end() > stack.get(stack.size() - 1).end()) {
                            stack.remove(stack.size() - 1);
                        }
                        if (box.boxType.equals(new BoxType("uuid"))
                                || box.boxType.equals(psshBoxType)) {
                            limitedParsingEnd = box.end();
                        }
                        if (stack.size() == 0) {
                            if (box.boxType.equals(moovBoxType)) {
                                limitedParsingEnd = box.end();
                            }
                            mFileStructure.put(box.boxType.type, box);
                        } else {
                            stack.get(stack.size() - 1).subBoxes.put(box.boxType.type, box);
                        }
                        if (!mExtendedParsing && mOffset == limitedParsingEnd) {
                            break;
                        }
                        stack.add(box);
                        box = readBox(fileSize);
                    } while (box != null);
                }
            }
            mFile.close();
        } catch (IOException e) {
            DrmLog.logException(e);
        }
        DrmLog.debug("end");
    }

    private byte[] getPlayReadyObjects() {
        DrmLog.debug("start");
        byte[] result = null;
        Box box = mFileStructure.get(Helper.BoxTypeForName("moov"), Box.EmptyBox).subBoxes.get(
                Helper.BoxTypeForName("uuid"), Box.EmptyBox);
        if (box.boxType.equals(protSysSpecificHeaderBoxType)) {
            Boxes.ProtSysSpecificHeaderBox derivedBox = (Boxes.ProtSysSpecificHeaderBox)box;
            if (derivedBox.systemId.equals(playReadySystemId)) {
                result = derivedBox.data;
            }
        }
        if (result == null) {
            Box boxUV = mFileStructure.get(Helper.BoxTypeForName("moov"), Box.EmptyBox).subBoxes
                    .get(Helper.BoxTypeForName("pssh"), Box.EmptyBox);
            if (boxUV != null && boxUV.boxType.equals(psshBoxType)) {
                Boxes.ProtSysSpecificHeaderBox derivedBox = (Boxes.ProtSysSpecificHeaderBox)boxUV;
                if (derivedBox.systemId.equals(playReadySystemId)) {
                    result = derivedBox.data;
                }
            }
        }
        DrmLog.debug("end");
        return result;
    }
}
