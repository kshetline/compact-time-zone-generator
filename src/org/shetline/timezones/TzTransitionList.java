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

import java.io.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.zone.*;
import java.util.*;
import java.util.regex.*;

import static java.lang.Math.abs;
import static org.shetline.timezones.TzUtil.*;


public class TzTransitionList extends ArrayList<TzTransition>
{
  private String          zoneId = null;
  private IanaZoneRecord  lastZoneRec = null;
  private boolean         fromJava = false;

  private static final Pattern  systemV = Pattern.compile("SystemV/(\\w\\w\\w)\\d(\\w\\w\\w)");
  private static final int      ZONE_MATCHING_TOLERANCE = 60 * 24 * 30 * 3; // Three months, in minutes.

  public TzTransitionList()
  {
  }

  public TzTransitionList(String zoneId)
  {
    this.zoneId = zoneId;
  }

  public static TzTransitionList getTzTransitionListJavaTime(String zoneId, int minYear, int maxYear)
  {
    ZoneRules zone;

    try {
      zone = ZoneRulesProvider.getRules(zoneId, true);
    }
    catch (ZoneRulesException e) {
      return null;
    }

    TzTransitionList            transitions = new TzTransitionList(zoneId);
    long                        lastSampleTime = MIN_JS_SAFE_INTEGER;
    List<ZoneOffsetTransition>  zoneTransitions = zone.getTransitions();
    int                         offset;
    int                         dstOffset;
    String                      stdName = null;
    String                      dstName = null;
    Matcher                     matcher = systemV.matcher(zoneId);

    if (matcher.matches()) {
      stdName = matcher.group(1);
      dstName = matcher.group(2);
    }

    if (zoneTransitions.size() > 0) {
      offset = div(zoneTransitions.get(0).getOffsetBefore().getTotalSeconds() + 30, 60);
      dstOffset = (int) zone.getDaylightSavings(zoneTransitions.get(0).getInstant().minus(1, ChronoUnit.MINUTES)).get(ChronoUnit.SECONDS) / 60;
    }
    else {
      ZonedDateTime   earliest = ZonedDateTime.of(minYear, 1, 1, 0, 0, 0, 0, ZoneId.of(zoneId));

      offset = div(zone.getStandardOffset(earliest.toInstant()).getTotalSeconds() + 30, 60);
      dstOffset = (int) (zone.getDaylightSavings(earliest.toInstant()).get(ChronoUnit.SECONDS) + 30) / 60;
    }

    transitions.add(new TzTransition(lastSampleTime, offset, dstOffset, (dstOffset == 0 ? stdName : dstName)));

    for (ZoneOffsetTransition zoneTransition : zoneTransitions) {
      long  tTime = div(zoneTransition.getInstant().getEpochSecond() + 30, 60);

      offset = div(zoneTransition.getOffsetAfter().getTotalSeconds() + 30, 60);
      dstOffset = (int) zone.getDaylightSavings(zoneTransition.getInstant()).get(ChronoUnit.SECONDS) / 60;
      transitions.add(new TzTransition(tTime, offset, dstOffset, (dstOffset == 0 ? stdName : dstName)));
      lastSampleTime = tTime;
    }

    if (lastSampleTime != MIN_JS_SAFE_INTEGER) {
      lastSampleTime += 60 * 12; // Add half a day

      while (ZonedDateTime.ofInstant(Instant.ofEpochSecond(lastSampleTime * 60), ZoneId.of(zoneId)).getYear() <= maxYear) {
        ZoneOffsetTransition  zoneTransition = zone.nextTransition(Instant.ofEpochSecond(lastSampleTime * 60));

        if (zoneTransition == null)
          break;

        long  tTime = div(zoneTransition.getInstant().getEpochSecond() + 30, 60);

        offset = div(zoneTransition.getOffsetAfter().getTotalSeconds() + 30, 60);
        dstOffset = (int) zone.getDaylightSavings(zoneTransition.getInstant()).get(ChronoUnit.SECONDS) / 60;
        transitions.add(new TzTransition(tTime, offset, dstOffset, (dstOffset == 0 ? stdName : dstName)));
        lastSampleTime = tTime;
      }
    }

    transitions.fromJava = true;
    transitions.trim(minYear, maxYear);

    return transitions;
  }

  public static TzTransitionList getZoneTransitionsFromZoneinfo(String zoneInfoPath, String zoneId)
  {
    // Derived from bsmi.util.ZoneInfo.java, http://bmsi.com/java/ZoneInfo.java, Copyright (C) 1999 Business Management Systems, Inc.
    TzTransitionList  transitions = new TzTransitionList(zoneId);
    File              file = new File(zoneInfoPath + File.separator + zoneId);

    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      int  transitionCount;
      int  offsetCount;
      int  nameChars;

      //noinspection ResultOfMethodCallIgnored
      in.skip(32);
      transitionCount = in.readInt();
      offsetCount = in.readInt();
      nameChars = in.readInt();

      int[]   times = new int[transitionCount];
      byte[]  offsetIndices = new byte[transitionCount];

      for (int i = 0; i < transitionCount; ++i)
        times[i] = in.readInt();

      in.readFully(offsetIndices);

      int[]       offsets = new int[offsetCount];
      boolean[]   dsts = new boolean[offsetCount];
      byte[]      nameIndices = new byte[offsetCount];
      byte[]      nameBytes = new byte[nameChars];
      String[]    names = new String[offsetCount];

      for (int i = 0; i < offsetCount; ++i) {
        offsets[i] = in.readInt();
        dsts[i] = (in.readByte() != 0);
        nameIndices[i] = in.readByte();
      }

      in.readFully(nameBytes);

      for (int i = 0; i < offsetCount; ++i) {
        int   index = nameIndices[i];
        int   end = index;

        while (nameBytes[end] != 0)
          ++end;

        names[i] = new String(nameBytes, index, end - index, "UTF-8");
      }

      for (int i = 0; i < transitionCount; ++i) {
        int     index = offsetIndices[i];
        long    tTime;
        int     offset = div(offsets[index] + 30, 60);
        int     dst = (dsts[index] ? 60 : 0); // Not always accurate to use 60 here, but we're just going to care about 0 vs. non-zero later.
        String  name = names[index];

        if (times[i] == Integer.MIN_VALUE)
          tTime = MIN_JS_SAFE_INTEGER;
        else
          tTime = div(times[i] + 30, 60);

        if (name.startsWith("+") || name.startsWith("-"))
          name = null;

        transitions.add(new TzTransition(tTime, offset, dst, name));
      }
    }
    catch (IOException e) {
      return null;
    }

    transitions.removeDuplicateTransitions();

    return transitions;
  }

  public static TzTransitionList parseCompactZoneTable(String table)
  {
    TzTransitionList  transitions = new TzTransitionList();
    String[]          sections = table.split(";");
    String[]          parts = sections[0].split(" ");
    int               utcOffset = parseOffsetNotation(parts[0]);
    TzTransition      tzt = new TzTransition(MIN_JS_SAFE_INTEGER, utcOffset, 0, null);

    transitions.add(tzt);

    if (sections.length > 1) {
      String[]  offsets = sections[1].split(" ");
      int[]     utcOffsets = new int[offsets.length];
      int[]     dstOffsets = new int[offsets.length];
      String[]  names = new String[offsets.length];

      for (int i = 0; i < offsets.length; ++i) {
        String  offset = offsets[i];

        parts = offset.split("/");
        utcOffsets[i] = (int) fromBase60(parts[0]);
        dstOffsets[i] = (int) fromBase60(parts[1]);

        if (parts.length > 2)
          names[i] = parts[2];
        else
          names[i] = null;
      }

      tzt.name = names[0];

      if (sections.length > 3) {
        String    offsetIndices = sections[2];
        String[]  transitionTimes = sections[3].split(" ");
        long      lastTTime = 0;

        for (int i = 0; i < offsetIndices.length(); ++i) {
          int   offsetIndex = (int) fromBase60(offsetIndices.substring(i, i + 1));
          long  tTime = lastTTime + fromBase60(transitionTimes[i]);

          tzt = new TzTransition(tTime, utcOffsets[offsetIndex], dstOffsets[offsetIndex], names[offsetIndex]);
          transitions.add(tzt);
          lastTTime = tTime;
        }
      }
    }

    return transitions;
  }

  public String getZoneId() {
    return zoneId;
  }

  public IanaZoneRecord getLastZoneRec()
  {
    return lastZoneRec;
  }

  public void setLastZoneRec(IanaZoneRecord lastZoneRec)
  {
    this.lastZoneRec = lastZoneRec;
  }

  public boolean findCalendarRollbacks(boolean fixRollbacks, boolean showWarnings)
  {
    boolean   hasRollbacks = false;
    boolean   warningShown = false;

    for (int i = 1; i < size(); ++i) {
      TzTransition    prev = get(i - 1);
      TzTransition    curr = get(i);
      LocalDateTime   before = LocalDateTime.ofEpochSecond((curr.time - 1) * 60, 0, ZoneOffset.ofTotalSeconds(prev.utcOffset * 60));
      LocalDate       beforeDate = before.toLocalDate();
      LocalDateTime   after = LocalDateTime.ofEpochSecond(curr.time * 60, 0, ZoneOffset.ofTotalSeconds(curr.utcOffset * 60));
      LocalDate       afterDate = after.toLocalDate();

      if (afterDate.compareTo(beforeDate) < 0) {
        hasRollbacks = true;

        LocalDateTime   turnbackTime = LocalDateTime.ofEpochSecond(curr.time * 60, 0, ZoneOffset.ofTotalSeconds(prev.utcOffset * 60));
        LocalDateTime   midnight = LocalDateTime.of(turnbackTime.getYear(), turnbackTime.getMonth(), turnbackTime.getDayOfMonth(), 0, 0);
        int             forayIntoNextDay = (int) midnight.until(turnbackTime, ChronoUnit.MINUTES);

        if (showWarnings && !warningShown) {
          System.out.print("* Warning -- " + zoneId + ": " + before.format(dateTimeFormat) + " rolls back to " + after.format(dateTimeFormat) +
            " (" + forayIntoNextDay + " minute foray into next day)");
          warningShown = true;
        }

        if (fixRollbacks)
          curr.time -= forayIntoNextDay;
      }
    }

    boolean   stillHasRollbacks = false;

    if (hasRollbacks && fixRollbacks)
      stillHasRollbacks = findCalendarRollbacks(false, false);

    if (warningShown) {
      if (fixRollbacks) {
        if (stillHasRollbacks)
          System.out.print(" *** NOT FIXED ***");
        else
          System.out.print(" * fixed *");
      }

      System.out.println();
    }
    else if (stillHasRollbacks)
      System.err.println("*** Failed to fix calendar rollbacks in " + zoneId);

    return hasRollbacks;
  }

  public void removeDuplicateTransitions()
  {
    for (int i = 1; i < size(); ++i) {
      TzTransition  prev = get(i - 1);
      TzTransition  curr = get(i);

      if (curr.time == prev.time ||
          curr.utcOffset == prev.utcOffset && curr.dstOffset == prev.dstOffset && equal(curr.name, prev.name))
        remove(i--);
    }
  }

  public void trim(int minYear, int maxYear)
  {
    if (minYear != Integer.MIN_VALUE) {
      // Find the latest Standard Time transition before minYear. Change the start time of that
      // transition to the programmatic beginning of time, and delete all other transitions before it.
      int           match = -1;
      TzTransition  tzt;

      for (int i = 0; i < size(); ++i) {
        tzt = get(i);

        if (tzt.time == MIN_JS_SAFE_INTEGER)
          continue;

        LocalDateTime   ldt = LocalDateTime.ofEpochSecond((tzt.time + 1) * 60, 0, ZoneOffset.ofTotalSeconds(tzt.utcOffset * 60));

        if (ldt.getYear() >= minYear)
          break;
        else if (tzt.dstOffset == 0)
          match = i;
      }

      if (match >= 0) {
        removeRange(0, match);
        get(0).time = MIN_JS_SAFE_INTEGER;
      }
    }

    // End on a transition to Standard Time within the proper year range
    for (int i = size() - 1; i >= 0; --i) {
      TzTransition  tzt = get(i);

      if (tzt.time == MIN_JS_SAFE_INTEGER)
        continue;

      LocalDateTime   ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(tzt.time * 60 + tzt.utcOffset), ZoneId.of("UTC"));

      if (tzt.dstOffset != 0 || ldt.getYear() > maxYear)
        remove(i);
      else
        break;
    }
  }

  public String createCompactTransitionTable()
  {
    return createCompactTransitionTable(false);
  }

  // The format produced here borrows some key ideas, like the use of base-60 numbers, from the moment.js timezone package.
  // https://momentjs.com/timezone/
  //
  // Though somewhat similar in appearance, the format is not compatible.
  public String createCompactTransitionTable(boolean fixCalendarRollbacks)
  {
    StringBuilder   sb = new StringBuilder();
    int             baseOffset = get(0).utcOffset;
    int             nominalStdOffset = 0;
    int             nominalDstOffset = 0;
    TzRule          finalStdRule = null;
    TzRule          finalDstRule = null;

    if (fromJava) {
      ZoneRules   zone = ZoneRulesProvider.getRules(zoneId, true);
      boolean     needStdOffset = true;
      boolean     needDstOffset = true;
      List<ZoneOffsetTransition>  zoneTransitions = zone.getTransitions();

      if (zoneTransitions.size() > 0) {
        Instant   lastTransitionTime = zoneTransitions.get(zoneTransitions.size() - 1).getInstant();

        for (int i = 0; i < 4 && (needStdOffset || needDstOffset); ++i) {
          ZoneOffsetTransition  transition = zone.nextTransition(lastTransitionTime);

          if (transition != null) {
            lastTransitionTime = transition.getInstant();

            needStdOffset = false;
            nominalStdOffset = zone.getStandardOffset(lastTransitionTime).getTotalSeconds() / 60;

            if (zone.isDaylightSavings(lastTransitionTime)) {
              needDstOffset = false;
              nominalDstOffset = zone.getOffset(lastTransitionTime).getTotalSeconds() / 60 - nominalStdOffset;
            }
          }
          else
            break;
        }
      }

      if (needStdOffset || needDstOffset) {
        TimeZone  oldStyleZone = TimeZone.getTimeZone(zoneId);

        if (needStdOffset)
          nominalStdOffset = div(oldStyleZone.getRawOffset(), MINUTE_MSEC);

        nominalDstOffset = oldStyleZone.getDSTSavings() / MINUTE_MSEC;
      }
    }
    else {
      boolean   lookingForStd = true;
      boolean   lookingForStdRule = true;
      boolean   lookingForDst = true;
      String    lastRuleSet = null;

      if (lastZoneRec != null && lastZoneRec.rules == null) {
        nominalStdOffset = lastZoneRec.gmtOffset;
        lookingForStd = lookingForDst = false;
      }

      for (int i = size() - 1; i >= 0 && (lookingForStd || lookingForStdRule || lookingForDst); --i) {
        TzTransition  tzt = get(i);

        if (tzt.rule == null) {
          if (lookingForStd)
            nominalStdOffset = tzt.utcOffset - tzt.dstOffset;

          if (lookingForDst)
            nominalDstOffset = tzt.dstOffset;

          break;
        }

        if (lastRuleSet == null)
          lastRuleSet = tzt.rule.name;
        else if (!tzt.rule.name.equals(lastRuleSet))
          break;

        if (lookingForStd) {
          nominalStdOffset = tzt.utcOffset - tzt.dstOffset;
          lookingForStd = false;
        }

        if (lookingForStdRule && tzt.dstOffset == 0 && tzt.rule.endYear == Integer.MAX_VALUE) {
          finalStdRule = tzt.rule;
          lookingForStdRule = false;
        }

        if (lookingForDst && tzt.dstOffset != 0 && tzt.rule.endYear == Integer.MAX_VALUE) {
          nominalDstOffset = tzt.dstOffset;
          finalDstRule = tzt.rule;
          lookingForDst = false;
        }
      }
    }

    sb.append(formatOffsetNotation(baseOffset)).append(' ').append(formatOffsetNotation(nominalStdOffset))
      .append(' ').append(nominalDstOffset).append(';');

    List<String>  uniqueOffsetList = new ArrayList<>();
    List<String>  offsetList = new ArrayList<>();

    for (TzTransition t : this) {
      String  offset = toBase60(t.utcOffset) + "/" + toBase60(t.dstOffset);

      if (t.name != null && t.name.length() != 0)
        offset += "/" + t.name;

      if (!uniqueOffsetList.contains(offset))
        uniqueOffsetList.add(offset);

      offsetList.add(offset);
    }

    for (String offset : uniqueOffsetList)
      sb.append(offset).append(' ');

    sb.setLength(sb.length() - 1);
    sb.append(';');

    for (int i = 1; i < size(); ++i)
      sb.append(toBase60(uniqueOffsetList.indexOf(offsetList.get(i))));

    sb.append(';');

    long  lastTime = 0;

    for (int i = 1; i < size(); ++i) {
      TzTransition  t = get(i);

      sb.append(toBase60(t.time - lastTime)).append(' ');
      lastTime = t.time;
    }

    sb.setLength(sb.length() - 1);

    if (finalStdRule != null && finalDstRule != null) {
      if (fixCalendarRollbacks) {
        TzRule  fallBackRule = finalStdRule;
        TzRule  aheadRule = finalDstRule;
        int     fallBackAmount = finalDstRule.save;

        if (fallBackAmount < 0) {
          fallBackRule = finalDstRule;
          aheadRule = finalStdRule;
          fallBackAmount *= -1;
        }

        int   turnbackTime = fallBackRule.atHour * 60 + fallBackRule.atMinute;

        if (fallBackRule.atType == CLOCK_TYPE_UTC)
          turnbackTime += nominalStdOffset + aheadRule.save;
        else if (fallBackRule.atType == CLOCK_TYPE_STD)
          turnbackTime += aheadRule.save;

        if (turnbackTime > 0 && turnbackTime - fallBackAmount < 0) {
          fallBackRule.atMinute -= turnbackTime;

          while (fallBackRule.atMinute < 0) {
            fallBackRule.atMinute += 60;
            --fallBackRule.atHour;
          }
        }
      }

      sb.append(';').append(finalStdRule.toCompactTailRule()).append(',').append(finalDstRule.toCompactTailRule());
    }

    return sb.toString();
  }

  private List<TzTransition> cloneTransitions()
  {
    List<TzTransition>  copy = new ArrayList<>(size());

    for (TzTransition tzt : this)
      copy.add((TzTransition) tzt.clone());

    return copy;
  }

  public boolean closelyMatchesJavaTransitions(TzTransitionList fromJava)
  {
    // Java transition list is likely shorter since it is trimmed off before 1900, and it doesn't
    // contain some transitions like name-only changes. It is possible for Java to have a transition
    // lacking in the compiled transitions where Java includes a transition below the compiler's
    // one-minute round-off.
    //
    // Mostly what we want to find are differences of hours, not days -- it's the hour differences
    // which are most likely to be caused by the zone compilation issues we're looking for.
    //

    List<TzTransition>  transitions = cloneTransitions();

    // Throw away any transitions where there was only a name change. These won't be present in the Java
    // transitions.
    for (int i = 1; i < transitions.size(); ++i) {
      TzTransition  prev = transitions.get(i - 1);
      TzTransition  curr = transitions.get(i);

      if (curr.utcOffset == prev.utcOffset && curr.dstOffset == prev.dstOffset)
        transitions.remove(i--);
    }

    for (int i = 1, j = 1; i < transitions.size() && j < fromJava.size(); ++i, ++j) {
      TzTransition  t = transitions.get(i);
      TzTransition  tj = fromJava.get(j);

      if (t.time + ZONE_MATCHING_TOLERANCE < tj.time) {
        --i;
        continue;
      }
      else if (tj.time + ZONE_MATCHING_TOLERANCE < t.time) {
        --j;
        continue;
      }

      // Allow for slightly different rounding off of seconds to minutes.
      if (abs(t.time - tj.time) > 1 ||
          abs(t.utcOffset      - tj.utcOffset) > 1 ||
              t.dstOffset     != tj.dstOffset)
      {
        System.err.println("index: " + i);
        System.err.println("  1: " + t.time + ", " + t.utcOffset + ", " + t.dstOffset + ": " + t.formatTime());
        System.err.println("  2: " + tj.time + ", " + tj.utcOffset + ", " + tj.dstOffset + ": " + tj.formatTime());
        System.err.println("  -: " + (tj.time - t.time));

        return false;
      }
    }

    return true;
  }

  public boolean closelyMatchesZoneinfoTransitions(TzTransitionList fromZoneinfo)
  {
    // ZoneInfo transition list might include transitions missing from our compiled transitions because
    // they're below our one-minute resolution.
    //
    // The ZoneInfo transition list might end sooner, because it's truncated at 2037.
    //
    // Mostly what we want to find are differences of hours, not days -- it's the hour differences
    // which are most likely to be caused by the zone compilation issues we're looking for.

    for (int i = 1, j = 1; i < size() && j < fromZoneinfo.size(); ++i, ++j) {
      TzTransition  t = get(i);
      TzTransition  tzi = fromZoneinfo.get(j);

      if (t.time + ZONE_MATCHING_TOLERANCE < tzi.time) {
        --i;
        continue;
      }
      else if (tzi.time + ZONE_MATCHING_TOLERANCE < t.time) {
        --j;
        continue;
      }

      // Allow for slightly different rounding off of seconds to minutes.
      if (abs(t.time - tzi.time) > 1 ||
          abs(t.utcOffset         - tzi.utcOffset) > 1 ||
              (t.dstOffset == 0) != (tzi.dstOffset == 0) ||
              !equal(t.name, tzi.name))
      {
        System.err.println("index: " + i);
        System.err.println("  1: " + t.time + ", " + t.utcOffset + ", " + t.dstOffset + ", " + t.name + ": " + t.formatTime());
        System.err.println("  2: " + tzi.time + ", " + tzi.utcOffset + ", " + tzi.dstOffset + ", " + tzi.name + ": " + tzi.formatTime());
        System.err.println("  -: " + (tzi.time - t.time));

        return false;
      }
    }

    return true;
  }

  public boolean transitionsMatch(TzTransitionList otherList)
  {
    if (size() != otherList.size()) {
      System.err.println(size() + " != " + otherList.size());

      return false;
    }

    for (int i = 0; i < size(); ++i) {
      TzTransition  ti1 = get(i);
      TzTransition  ti2 = otherList.get(i);

      if (ti1.time != ti2.time ||
          ti1.utcOffset      != ti2.utcOffset ||
          ti1.dstOffset      != ti2.dstOffset ||
          !equal(ti1.name, ti2.name))
      {
        System.err.println("index: " + i);
        System.err.println("  1: " + ti1.time + ", " + ti1.utcOffset + ", " + ti1.dstOffset + ", " + ti1.name + ": " + ti1.formatTime());
        System.err.println("  2: " + ti2.time + ", " + ti2.utcOffset + ", " + ti2.dstOffset + ", " + ti2.name + ": " + ti2.formatTime());
        System.err.println("  -: " + (ti2.time - ti1.time));

        return false;
      }
    }

    return true;
  }

  public void dump(OutputStream out) {
    try {
      dump(new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, "UTF-8"))));
    }
    catch (UnsupportedEncodingException e) {}
  }

  public void dump(PrintWriter out)
  {
    out.println("-------- " + zoneId + " --------");

    if (size() == 0)
      out.println("(empty)");
    else if (size() == 1) {
      TzTransition  tzt = get(0);

      out.println("Fixed UTC offset at " + formatOffsetNotation(tzt.utcOffset) + (tzt.name != null ? " " + tzt.name : ""));
    }
    else {
      TzTransition  tzt = get(0);

      out.println("____-__-__ __:__ ±____ ±____ --> ____-__-__ __:__ " +
                  formatOffsetNotation(tzt.utcOffset) + " " + formatOffsetNotation(tzt.dstOffset) +
                  (tzt.name != null ? " " + tzt.name : ""));

      for (int i = 1; i < size(); ++i) {
        TzTransition    prev = get(i - 1);
        ZoneOffset      prevOffset = ZoneOffset.ofTotalSeconds(prev.utcOffset * 60);
        TzTransition    curr = get(i);
        ZoneOffset      currOffset = ZoneOffset.ofTotalSeconds(curr.utcOffset * 60);
        LocalDateTime   prevDateTime = LocalDateTime.ofEpochSecond((curr.time - 1) * 60, 0, prevOffset);
        LocalDateTime   currDateTime = LocalDateTime.ofEpochSecond(curr.time * 60, 0, currOffset);

        out.println(prevDateTime.format(dateTimeFormat) + " " + formatOffsetNotation(prev.utcOffset) + " " + formatOffsetNotation(prev.dstOffset) + " --> " +
                    currDateTime.format(dateTimeFormat) + " " + formatOffsetNotation(curr.utcOffset) + " " + formatOffsetNotation(curr.dstOffset) +
                    (curr.name != null ? " " + curr.name : "") + (curr.dstOffset != 0 ? "*" : ""));
      }
    }
  }
}
