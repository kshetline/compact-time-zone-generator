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

import static org.shetline.timezones.TzUtil.*;


public class TzRule
{
  protected String  name;
  protected int     startYear;
  protected int     endYear;
  protected int     month;
  /**
   * If negative, find dayOfWeek on or before the absolute value of this value in the given month.
   * If 0, find the last dayOfWeek in the given month.
   * If positive, and dayOfWeek is negative, match this exact date.
   * If positive, and dayOfWeek is positive, find dayOfWeek on or after this value in the given month.
   */
  protected int     dayOfMonth;
  /**
   * 1 for Sunday through 7 for Saturday, negative when day of week doesn't matter (exact date is given).
   */
  protected int     dayOfWeek;
  protected int     atHour;
  protected int     atMinute;
  protected int     atType;
  protected int     save;
  protected String  letters;

  public static TzRule parseRule(String line)
  {
    TzRule    rule = new TzRule();
    String[]  parts = line.split("\\s+");
    int       pos;

    rule.name = parts[1];

    if ("min".equalsIgnoreCase(parts[2]) || "minimum".equalsIgnoreCase(parts[2]))
      rule.startYear = Integer.MIN_VALUE;
    else
      rule.startYear = to_int(parts[2]);

    if ("only".equalsIgnoreCase(parts[3]))
      rule.endYear = rule.startYear;
    else if ("max".equalsIgnoreCase(parts[3]) || "maximum".equalsIgnoreCase(parts[3]))
      rule.endYear = Integer.MAX_VALUE;
    else
      rule.endYear = to_int(parts[3]);

    rule.month = indexOfFailNotFound(MONTHS, parts[5].substring(0, 3)) / 3 + 1;

    if (parts[6].startsWith("last")) {
      rule.dayOfMonth = 0;
      rule.dayOfWeek = indexOfFailNotFound(DAYS, parts[6].substring(4, 7)) / 3 + 1;
    }
    else if ((pos = parts[6].indexOf(">=")) > 0) {
      rule.dayOfMonth = to_int(parts[6].substring(pos + 2));
      rule.dayOfWeek = indexOfFailNotFound(DAYS, parts[6].substring(0, 3)) / 3 + 1;
    }
    else if (parts[6].contains("<=")) {
      rule.dayOfMonth = -to_int(parts[6].substring(pos + 2));
      rule.dayOfWeek = indexOfFailNotFound(DAYS, parts[6].substring(0, 3)) / 3 + 1;
    }
    else {
      rule.dayOfMonth = to_int(parts[6]);
      rule.dayOfWeek = -1;
    }

    int[]   hmc = parseAtTime(parts[7]);

    rule.atHour = hmc[0];
    rule.atMinute = hmc[1];
    rule.atType = hmc[2];
    rule.save = parseOffsetTime(parts[8], true);

    if (parts.length < 10 || parts[9].equals("-"))
      rule.letters = "";
    else
      rule.letters = parts[9];

    return rule;
  }

  public String toCompactTailRule()
  {
    return startYear + " " + month + " " + dayOfMonth + " " + dayOfWeek + " " + atHour + ":" + atMinute + " " + atType + " " + (save / 60);
  }

  public String toString() {
    return name + ": " + startYear + ", " + endYear + "," + month + ", " + dayOfMonth + ", " + dayOfWeek + ", " +
            atHour + ":" + padLeft(atMinute, '0', 2) + (atType == CLOCK_TYPE_WALL ? "w" : (atType == CLOCK_TYPE_STD ? "s" : "u")) + ", " + (save / 60) + ", " + letters;
  }
}
