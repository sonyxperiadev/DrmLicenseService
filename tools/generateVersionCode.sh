#!/bin/bash
# ***** BEGIN LICENSE BLOCK *****
# Version: MPL 1.1
#
# The contents of this file are subject to the Mozilla Public License Version
# 1.1 (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
# http://www.mozilla.org/MPL/
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
# for the specific language governing rights and limitations under the
# License.
#
# The Original Code is DRM License Service.
#
# The Initial Developer of the Original Code is
# Sony Mobile Communications AB.
# Portions created by Sony Mobile Communications AB are Copyright (C) 2012
# Sony Mobile Communications AB. All Rights Reserved.
#
# Contributor(s):
#
# ***** END LICENSE BLOCK *****

# Convert versionName (01.040.0101) to integer to be used in versionCode.
# 0101 is converted to 14 LSB bits
# 040 is converted to 10 middle bits
# 01 is converted to 7 MSB bits
# Concaternated binary value is converted to decimal (and should be used
# as versionCode)

# VersionName Rule
# Major Version (2 digit number)
#  01= Contributed DLS
#  Other = Suggested DLS
# Minor Version (1 digit number + 2 digit number)
#  023= GB (2.3.X)
#  040 = ICS (4.0.X)
#  Ex 050 = JellyBean (5.0.X)
# Revision　(2 digit number + 2 digit number)
#  0001 = function version 00 bugfix version 01
#  If function add 0001 -> 0101
#  And bugfix 0101 -> 0102
#  And function add 0102 -> 0201
#

if [ $# -ne 1 ] ; then
  echo "Missing parameter: <path>/AndroidManifest.xml"
  exit -1
fi

if [ ! -f $1 ] ; then
  echo "Can not find file: $1"
  exit -1
fi

values=`grep android:versionName $1 | sed 's+.*\"\(.*\)\".*+\1+g'`

v1=`echo $values | cut -d'.' -f1 | xargs echo "ibase=10;obase=2;" | bc | \
  awk '{i=07; while (i>length($1)) {printf 0 ; i--} printf $1}'`

v2=`echo $values | cut -d'.' -f2 | xargs echo "ibase=10;obase=2;" | bc | \
  awk '{i=10; while (i>length($1)) {printf 0 ; i--} printf $1}'`

v3=`echo $values | cut -d'.' -f3 | xargs echo "ibase=10;obase=2;" | bc | \
  awk '{i=14; while (i>length($1)) {printf 0 ; i--} printf $1}'`

vCode=`echo "ibase=2;$v1$v2$v3;obase=10" | bc`

sed -e "s/\(android:versionCode=\"\)[0-9]*\"/\1$vCode\"/g" -i $1
