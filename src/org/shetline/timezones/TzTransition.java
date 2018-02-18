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

import static org.shetline.timezones.TzUtil.*;


public class TzTransition implements Cloneable
{
  protected long    time; // in seconds from epoch
  protected int     utcOffset; // seconds, positive eastward from UTC
  protected int     dstOffset; // seconds
  protected String  name;
  protected TzRule  rule = null;

  public TzTransition(long time, int utcOffset, int dstOffset, String name)
  {
    this(time, utcOffset, dstOffset, name, null);
  }

  public TzTransition(long time, int utcOffset, int dstOffset, String name, TzRule rule)
  {
    this.time = time;
    this.utcOffset = utcOffset;
    this.dstOffset = dstOffset;
    this.name = name;
    this.rule = rule;
  }

  @Override
  public Object clone()
  {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {}

    return null; // Should be impossible to end up here
  }

  public String formatTime()
  {
    LocalDateTime   ldt = LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.ofTotalSeconds(utcOffset));

    return ldt.format(dateTimeFormat);
  }

  public String toString()
  {
    String  s;

    if (time == MIN_JS_SAFE_INTEGER)
      s = "---";
    else
      s = formatTime();

    return s + ", " + formatOffsetNotation(utcOffset) + ", " + formatOffsetNotation(dstOffset) + ", " + name;
  }
}
