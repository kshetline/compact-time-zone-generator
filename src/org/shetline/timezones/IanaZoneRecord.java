/*
  Copyright © 2018 Kerry Shetline, kerry@shetline.com

  MIT license: https://opensource.org/licenses/MIT

  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
  persons to whom the Software is furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
  Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
  OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.shetline.timezones;

import java.time.*;
import java.time.temporal.ChronoUnit;

import static java.lang.Math.min;
import static org.shetline.timezones.TzUtil.*;


public class IanaZoneRecord
{
  protected int     gmtOffset;
  protected String  rules;
  protected String  format;
  protected long    until;
  protected int     untilType;

  private static final ZoneOffset   UTC = ZoneOffset.ofHours(0);

  public static IanaZoneRecord parseZoneRecord(String line, StringBuilder zoneId, boolean roundToMinutes)
  {
    // Unfortunately the use of tabs vs. spaces to delimit these files is wildly inconsistent,
    // so it takes some extra effort to parse correctly.
    String  parts[];

    if (line.startsWith("Zone")) {
      StringBuilder   sb = new StringBuilder();

      parts = line.split("\\s+");

      if (zoneId != null)
        zoneId.append(parts[1]);

      for (int i = 2; i < parts.length; ++i) {
        if (i > 2)
          sb.append(' ');

        sb.append(parts[i]);
      }

      line = sb.toString();
    }
    else {
      parts = line.trim().split("\\s+");
      line = join(parts, " ");
    }

    IanaZoneRecord zoneRec = new IanaZoneRecord();

    parts = line.split(" ");
    zoneRec.gmtOffset = parseOffsetTime(parts[0], roundToMinutes);
    zoneRec.rules = (parts[1].equals("-") ? null : parts[1]);
    zoneRec.format = parts[2];

    if (parts.length > 3) {
      StringBuilder   sb = new StringBuilder();

      for (int i = 3; i < parts.length; ++i) {
        if (i > 3)
          sb.append(' ');

        sb.append(parts[i]);
      }

      int[]           ymdhmsc = parseUntilTime(sb.toString(), roundToMinutes);
      int             clockType = ymdhmsc[6];
      LocalDateTime   ldt = LocalDateTime.of(ymdhmsc[0], ymdhmsc[1], ymdhmsc[2], min(ymdhmsc[3], 23), ymdhmsc[4], ymdhmsc[5]);

      if (ymdhmsc[3] == 24)
        ldt = ldt.plus(1, ChronoUnit.HOURS);

      zoneRec.until = ldt.toEpochSecond(UTC) - (clockType != CLOCK_TYPE_UTC ? zoneRec.gmtOffset : 0);
      zoneRec.untilType = clockType;
    }
    else
      zoneRec.until = MAX_JS_SAFE_INTEGER;

    return zoneRec;
  }

  public String toString()
  {
    String  s = gmtOffset + ", " + rules + ", " + format;

    if (until != MAX_JS_SAFE_INTEGER) {
      LocalDateTime   ldt = LocalDateTime.ofEpochSecond(until, 0, ZoneOffset.ofTotalSeconds(untilType != CLOCK_TYPE_UTC ? gmtOffset : 0));

      s += ", " + ldt.format(dateTimeFormat) + (untilType == CLOCK_TYPE_WALL ? "w" : (untilType == CLOCK_TYPE_STD ? "s" : "u"));
    }

    return s;
  }
}
