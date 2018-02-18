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

import java.time.format.DateTimeFormatter;
import java.util.regex.*;

import static java.lang.Math.abs;


public class TzUtil
{
  private TzUtil() {}

  public static final String    DAYS = "SunMonTueWedThuFriSat";
  public static final String    MONTHS = "JanFebMarAprMayJunJulAugSepOctNovDec";

  public static final int       LAST = 6; // Constant for getDateOfNthWeekdayOfMonth()

  public static final int   CLOCK_TYPE_WALL = 0;
  public static final int   CLOCK_TYPE_STD  = 1;
  public static final int   CLOCK_TYPE_UTC  = 2;

  public static final long  MIN_JS_SAFE_INTEGER = -0x1FFFFFFFFFFFFFL;
  public static final long  MAX_JS_SAFE_INTEGER =  0x1FFFFFFFFFFFFFL;

  public static final DateTimeFormatter   dateTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

  private static final Pattern  clockTypeMarker = Pattern.compile(".+\\d([gsuwz])$", Pattern.CASE_INSENSITIVE);

  public static boolean contains(Object[] array, Object obj)
  {
    return (indexOf(array, obj) >= 0);
  }

  // Safe for comparing two nulls.
  public static boolean equal(Object obj1, Object obj2)
  {
    return (obj1 == obj2 || (obj1 != null && obj1.equals(obj2)));
  }

  public static int div(int x, int y)
  {
    int   d = x / y;

    if ((x > 0 && y < 0 || x < 0 && y > 0) && x % y != 0)
      --d;

    return d;
  }

  public static long div(long x, long y)
  {
    long  d = x / y;

    if ((x > 0 && y < 0 || x < 0 && y > 0) && x % y != 0)
      --d;

    return d;
  }

  public static String formatOffsetNotation(int offset)
  {
    int   sign = (int) Math.signum(offset);

    offset = abs(offset);

    int     hrs = offset / 3600;
    int     min = (offset - hrs * 3600) / 60;
    int     sec = offset % 60;
    String  s = (sign < 0 ? "-" : "+") + padLeft(hrs, '0', 2) + padLeft(min, '0', 2);

    if (sec != 0)
      s += padLeft(sec, '0', 2);

    return s;
  }

  public static long fromBase60(String x, boolean multiplyBy60)
  {
    long  sign = 1;
    long  result = 0;

    if (x.startsWith("-")) {
      sign = -1;
      x = x.substring(1);
    }
    else if (x.startsWith("+"))
      x = x.substring(1);

    if (multiplyBy60) {
      int   pos = x.indexOf('.');

      if (pos >= 0) {
        if (pos == x.length() - 1)
          x = x.substring(0, pos) + '0';
        else
          x = x.substring(0, pos) + x.charAt(pos + 1);
      }
      else
        x += '0';
    }

    for (int i = 0; i < x.length(); ++i) {
      int   digit = x.charAt(i);

      if (digit > 96)
        digit -= 87;
      else if (digit > 64)
        digit -= 29;
      else
        digit -= 48;

      result *= 60;
      result += digit;
    }

    return result * sign;
  }

  public static int[] getDateFromDayNumber(long dayNum)
  {
    int   year;
    int   month;
    int   date;
    int   lastDay;

    year = (int) Math.floor((dayNum + 719528.0) / 365.2425);

    while (dayNum < getDayNumber(year, 1, 1))
      --year;

    while (dayNum >= getDayNumber(year + 1, 1, 1))
      ++year;

    date = (int) (dayNum - getDayNumber(year, 1, 1)) + 1;

    for (month = 1; date > (lastDay = getLastDateInMonth(year, month)); ++month)
      date -= lastDay;

    return new int[] {year, month, date, (int) dayNum};
  }

  public static int getDateOfNthWeekdayOfMonth(int year, int month, int dayOfTheWeek, int index)
  {
    boolean   last = (index >= LAST);
    int       date = 1;
    long      dayNum = getDayNumber(year, month, date);
    int       dayOfWeek = getDayOfWeek(dayNum);
    int[]     ymd;
    int       lastDate = 0;

    if (dayOfWeek == dayOfTheWeek && index == 1)
      return date;

    dayNum += mod(dayOfTheWeek - dayOfWeek, 7);
    ymd = getDateFromDayNumber(dayNum);

    while (ymd[1] == month) {
      lastDate = ymd[2];

      if (--index == 0)
        return lastDate;

      dayNum += 7;
      ymd = getDateFromDayNumber(dayNum);
    }

    if (last)
      return lastDate;
    else
      return 0;
  }

  // Day from epoch
  public static long getDayNumber(int year, int month, int date)
  {
    while (month <  1) { month += 12; --year; }
    while (month > 12) { month -= 12; ++year; }

    return 367L * year - div(7L * (year + (month + 9L) / 12), 4) - div(3L * ((year + (month - 9L) / 7) / 100 + 1), 4) +
      275 * month / 9 + date - 719559L;
  }

  // 1 for Sunday... 7 for Saturday.
  public static int getDayOfWeek(long dayNum)
  {
    return (int) mod(dayNum + 4, 7) + 1;
  }

  public static int getDayOnOrAfter(int year, int month, int dayOfTheWeek, int minDate)
  {
    long  dayNum = getDayNumber(year, month, minDate);
    int   dayOfWeek = getDayOfWeek(dayNum);

    minDate += mod(dayOfTheWeek - dayOfWeek, 7);

    if (minDate > getLastDateInMonth(year, month))
      minDate = 0;

    return minDate;
  }

  public static int getDayOnOrBefore(int year, int month, int dayOfTheWeek, int maxDate)
  {
    long  dayNum = getDayNumber(year, month, maxDate);
    int   dayOfWeek = getDayOfWeek(dayNum);

    maxDate -= mod(dayOfWeek - dayOfTheWeek, 7);

    if (maxDate < 0)
      maxDate = 0;

    return maxDate;
  }

  public static int getLastDateInMonth(int year, int month)
  {
    if (month == 9 || month == 4 || month == 6 || month == 11)
      return 30;
    else if (month != 2)
      return 31; // Works for pseudo-months 0 and 13 as well.
    else if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
      return 29;
    else
      return 28;
  }

  public static int indexOf(Object[] array, Object obj)
  {
    return indexOf(array, obj, 0);
  }

  public static int indexOf(Object[] array, Object obj, int startPos)
  {
    if (array == null)
      return -1;

    int   index = -1;

    for (int i = startPos; i < array.length; ++i) {
      if (equal(array[i], obj)) {
        index = i;
        break;
      }
    }

    return index;
  }

  public static int indexOfFailNotFound(String s, String sub)
  {
    int   pos = s.indexOf(sub);

    if (pos < 0)
      throw new RuntimeException("'" + sub + "' not found in '" + s + "'");

    return pos;
  }

  public static boolean isNullOrEmpty(String s)
  {
    return (s == null || s.isEmpty());
  }

  public static String join(Object[] items)
  {
    return join(items, ",");
  }

  public static String join(Object[] items, String delimiter)
  {
    StringBuilder   sb = new StringBuilder();

    if (items != null) {
      for (int i = 0; i < items.length; ++i) {
        Object  item = items[i];

        if (i > 0)
          sb.append(delimiter);

        sb.append(item != null ? item.toString() : "");
      }
    }

    return sb.toString();
  }

  public static int mod(int x, int y)
  {
    int   m = x % y;

    if ((m < 0 && y > 0) || (m > 0 && y < 0))
      return y + m;
    else
      return m;
  }

  public static long mod(long x, long y)
  {
    long  m = x % y;

    if ((m < 0 && y > 0) || (m > 0 && y < 0))
      return y + m;
    else
      return m;
  }

  public static String padLeft(int value, char padChar, int finalLength)
  {
    return padLeft("" + value, padChar, finalLength);
  }

  public static String padLeft(String s, char padChar, int finalLength)
  {
    if (s == null)
      return null;
    else if (s.length() >= finalLength)
      return s;

    return repeat(padChar, finalLength - s.length()) + s;
  }

  public static int[] parseAtTime(String s)
  {
    int[]     result = new int[] {0, 0, CLOCK_TYPE_WALL};
    Matcher   matcher = clockTypeMarker.matcher(s);

    if (matcher.matches()) {
      char  marker = matcher.group(1).toLowerCase().charAt(0);

      if (marker == 's')
        result[2] = CLOCK_TYPE_STD;
      else if (marker == 'g' || marker == 'u' || marker == 'z')
        result[2] = CLOCK_TYPE_UTC;

      s = right(s, -1);
    }

    String[]  parts = s.split(":");

    result[0] = to_int(parts[0]); // hour
    result[1] = to_int(parts[1]); // minute

    return result;
  }

  /**
   * Parse offset time in the form [+/-]hours[:minutes[:seconds]]
   * @param s Offset time as string
   * @param roundToMinutes If true, round to whole minutes.
   * @return Offset time in seconds
   */
  public static int parseOffsetTime(String s, boolean roundToMinutes)
  {
    int   sign = 1;

    if (s.startsWith("-")) {
      sign = -1;
      s = s.substring(1);
    }
    else if (s.startsWith("+"))
      s = s.substring(1);

    String[]  parts = s.split(":");
    int       hour = to_int(parts[0]);
    int       min = (parts.length > 1 ? to_int(parts[1]) : 0);
    int       sec = (parts.length > 2 ? (int) Math.round(to_double(parts[2])) : 0);

    if (roundToMinutes) {
      if (sec >= 30)
        ++min;

      sec = 0;
    }

    return sign * ((hour * 60 + min) * 60 + sec);
  }

  public static int parseOffsetNotation(String offset)
  {
    int   sign = 1;

    if (offset.startsWith("-")) {
      sign = -1;
      offset = offset.substring(1);
    }
    else if (offset.startsWith("+"))
      offset = offset.substring(1);

    if ("0".equals(offset))
      return 0;
    else if ("1".equals(offset))
      return sign * 3600;

    int   seconds = 3600 * to_int(offset.substring(0, 2)) + 60 * to_int(offset.substring(2, 4));

    if (offset.length() == 6)
      seconds += to_int(offset.substring(4, 6));

    return sign * seconds;
  }

  public static int[] parseUntilTime(String s, boolean roundToMinutes)
  {
    int[]     result = new int[] {0, 1, 1, 0, 0, 0, CLOCK_TYPE_WALL};
    Matcher   matcher = clockTypeMarker.matcher(s);

    if (matcher.matches()) {
      char  marker = matcher.group(1).toLowerCase().charAt(0);

      if (marker == 's')
        result[6] = CLOCK_TYPE_STD;
      else if (marker == 'g' || marker == 'u' || marker == 'z')
        result[6] = CLOCK_TYPE_UTC;

      s = right(s, -1);
    }

    String[]  parts = s.split("[ :]");

    result[0] = to_int(parts[0]); // year

    if (parts.length > 1) {
      result[1] = indexOfFailNotFound(MONTHS, parts[1].substring(0, 3)) / 3 + 1; // month

      if (parts.length > 2) {
        int   pos;

        // date
        if (parts[2].startsWith("last")) {
          int   dayOfWeek = indexOfFailNotFound(DAYS, parts[2].substring(4, 7)) / 3 + 1;

          result[2] = getDateOfNthWeekdayOfMonth(result[0], result[1], dayOfWeek, LAST);
        }
        else if ((pos = parts[2].indexOf(">=")) > 0) {
          int   dayOfMonth = to_int(parts[2].substring(pos + 2));
          int   dayOfWeek = indexOfFailNotFound(DAYS, parts[2].substring(0, 3)) / 3 + 1;

          result[2] = getDayOnOrAfter(result[0], result[1], dayOfWeek, dayOfMonth);
        }
        else if (parts[2].contains("<=")) {
          int   dayOfMonth = to_int(parts[2].substring(pos + 2));
          int   dayOfWeek = indexOfFailNotFound(DAYS, parts[2].substring(0, 3)) / 3 + 1;

          result[2] = getDayOnOrBefore(result[0], result[1], dayOfWeek, dayOfMonth);
        }
        else
          result[2] = to_int(parts[2]);

        if (parts.length > 3) {
          result[3] = to_int(parts[3]); // hour

          if (parts.length > 4) {
            result[4] = to_int(parts[4]); // minute

            if (parts.length > 5) {
              int   sec = (int) Math.round(to_double(parts[5])); // seconds

              if (roundToMinutes) {
                if (sec >= 30) {
                  ++result[4];

                  if (result[4] == 60) {
                    result[4] = 0;
                    ++result[3];

                    if (result[3] == 24) {
                      // In the rare event we get this far, just round off the seconds instead of rounding up.
                      result[3] = 23;
                      result[4] = 59;
                    }
                  }
                }
              }
              else
                result[5] = Math.min(sec, 59);
            }
          }
        }
      }
    }

    return result;
  }

  public static String repeat(char c, int count)
  {
    if (count < 0)
      return null;
    else if (count == 0)
      return "";

    StringBuilder   result = new StringBuilder(count);

    for (int i = 0; i < count; ++i)
      result.append(c);

    return result.toString();
  }

  public static String right(String s, int length)
  {
    if (s == null)
      return null;
    else if (length < 0 && s.length() <= -length)
      return "";
    else if (s.length() <= length)
      return s;

    if (length < 0)
      return s.substring(0, s.length() + length);
    else
      return s.substring(s.length() - length);
  }

  public static String rtrim(String s)
  {
    if (isNullOrEmpty(s))
      return s;

    int   i = s.length() - 1;

    //noinspection StatementWithEmptyBody
    while (s.charAt(i) <= 32 && --i >= 0) {}

    return s.substring(0, i + 1);
  }

  public static long signum(long x)
  {
    if (x < 0L)
      return -1L;
    else if (x > 0L)
      return 1L;
    else
      return 0L;
  }

  public static String toBase60(long x, boolean divideBy60)
  {
    StringBuilder   result = new StringBuilder();
    long            sign = 1;

    if (x < 0) {
      x *= -1;
      sign = -1;
    }

    if (x == 0)
      result.append('0');
    else {
      while (x > 0) {
        int   digit = (int) (x % 60);

        if (digit < 10)
          digit += 48;
        else if (digit < 36)
          digit += 87;
        else
          digit += 29;

        result.insert(0, (char) digit);

        x /= 60;
      }

      if (divideBy60) {
        if (result.length() < 2)
          result.insert(0, "0.");
        else
          result.insert(result.length() - 1, '.');
      }
    }

    if (sign < 0)
      result.insert(0, '-');

    String  s = result.toString();

    if (divideBy60 && s.endsWith(".0"))
      s = s.substring(0, s.length() - 2);

    return s;
  }

  public static double to_double(String s)
  {
    return to_double(s, 0.0);
  }

  public static double to_double(String s, double defaultValue)
  {
    if (s != null) {
      s = s.trim();

      if (s.startsWith("+"))
        s = s.substring(1);
    }
    else
      s = "";

    try {
      return Double.parseDouble(s);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static int to_int(String s)
  {
    return to_int(s, 0);
  }

  public static int to_int(String s, int defaultValue)
  {
    if (s != null) {
      s = s.trim();

      if (s.startsWith("+"))
        s = s.substring(1);
    }
    else
      s = "";

    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
