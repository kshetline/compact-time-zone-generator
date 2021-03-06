/*
  Copyright � 2018-2021 Kerry Shetline, kerry@shetline.com

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
import java.nio.charset.StandardCharsets;
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
  public String           aliasFor = null;

  private static final Pattern  systemV = Pattern.compile("SystemV/(\\w\\w\\w)\\d(\\w\\w\\w)");
  private static final int      ZONE_MATCHING_TOLERANCE = 3600 * 24 * 30 * 3; // Three months, in seconds.

  public TzTransitionList()
  {
  }

  public TzTransitionList(String zoneId)
  {
    this.zoneId = zoneId;
  }

  public TzTransitionList(String zoneId, String aliasFor)
  {
    this.zoneId = zoneId;
    this.aliasFor = aliasFor;
  }

  private static int conditionallyRoundToMinutes(int seconds, boolean roundToMinutes)
  {
    if (roundToMinutes)
      seconds = div(seconds + 30, 60) * 60;

    return seconds;
  }

  private static long conditionallyRoundToMinutes(long seconds, boolean roundToMinutes)
  {
    if (roundToMinutes)
      seconds = div(seconds + 30, 60) * 60;

    return seconds;
  }

  public static TzTransitionList getTzTransitionListJavaTime(String zoneId, int minYear, int maxYear, boolean roundToMinutes)
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
      offset = conditionallyRoundToMinutes(zoneTransitions.get(0).getOffsetBefore().getTotalSeconds(), roundToMinutes);
      dstOffset = (int) zone.getDaylightSavings(zoneTransitions.get(0).getInstant().minus(1, ChronoUnit.MINUTES)).get(ChronoUnit.SECONDS);
    }
    else {
      ZonedDateTime   earliest = ZonedDateTime.of(minYear, 1, 1, 0, 0, 0, 0, ZoneId.of(zoneId));

      offset = conditionallyRoundToMinutes(zone.getStandardOffset(earliest.toInstant()).getTotalSeconds(), roundToMinutes);
      dstOffset = (int) zone.getDaylightSavings(earliest.toInstant()).get(ChronoUnit.SECONDS);
    }

    transitions.add(new TzTransition(lastSampleTime, offset, dstOffset, (dstOffset == 0 ? stdName : dstName)));

    for (ZoneOffsetTransition zoneTransition : zoneTransitions) {
      long  tTime = conditionallyRoundToMinutes(zoneTransition.getInstant().getEpochSecond(), roundToMinutes);

      offset = conditionallyRoundToMinutes(zoneTransition.getOffsetAfter().getTotalSeconds(), roundToMinutes);
      dstOffset = (int) zone.getDaylightSavings(zoneTransition.getInstant()).get(ChronoUnit.SECONDS);
      transitions.add(new TzTransition(tTime, offset, dstOffset, (dstOffset == 0 ? stdName : dstName)));
      lastSampleTime = tTime;
    }

    if (lastSampleTime != MIN_JS_SAFE_INTEGER) {
      lastSampleTime += 3600 * 12; // Add half a day

      while (ZonedDateTime.ofInstant(Instant.ofEpochSecond(lastSampleTime), ZoneId.of(zoneId)).getYear() <= maxYear) {
        ZoneOffsetTransition  zoneTransition = zone.nextTransition(Instant.ofEpochSecond(lastSampleTime));

        if (zoneTransition == null)
          break;

        long  tTime = conditionallyRoundToMinutes(zoneTransition.getInstant().getEpochSecond(), roundToMinutes);

        offset = conditionallyRoundToMinutes(zoneTransition.getOffsetAfter().getTotalSeconds(), roundToMinutes);
        dstOffset = (int) zone.getDaylightSavings(zoneTransition.getInstant()).get(ChronoUnit.SECONDS);
        transitions.add(new TzTransition(tTime, offset, dstOffset, (dstOffset == 0 ? stdName : dstName)));
        lastSampleTime = tTime;
      }
    }

    transitions.fromJava = true;
    transitions.trim(minYear, maxYear);

    return transitions;
  }

  public static TzTransitionList getZoneTransitionsFromZoneinfo(String zoneInfoPath, String zoneId, boolean roundToMinutes)
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

        names[i] = new String(nameBytes, index, end - index, StandardCharsets.UTF_8);
      }

      for (int i = 0; i < transitionCount; ++i) {
        int     index = offsetIndices[i];
        long    tTime;
        int     offset = conditionallyRoundToMinutes(offsets[index], roundToMinutes);
        int     dst = (dsts[index] ? 3600 : 0); // Not always accurate to use 3600 here, but we're just going to care about 0 vs. non-zero later.
        String  name = names[index];

        if (times[i] == Integer.MIN_VALUE)
          tTime = MIN_JS_SAFE_INTEGER;
        else
          tTime = conditionallyRoundToMinutes(times[i], roundToMinutes);

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
        utcOffsets[i] = (int) Math.round(fromBase60(parts[0]) * 60);
        dstOffsets[i] = (int) Math.round(fromBase60(parts[1]) * 60);

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
          long  tTime = lastTTime + Math.round(fromBase60(transitionTimes[i]) * 60);

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

  public void setAliasFor(String original) {
    aliasFor = original;
  }

  public String getAliasFor() {
    return aliasFor;
  }

  public IanaZoneRecord getLastZoneRec()
  {
    return lastZoneRec;
  }

  public void setLastZoneRec(IanaZoneRecord lastZoneRec)
  {
    this.lastZoneRec = lastZoneRec;
  }

  public enum Rollbacks {NO_ROLLBACKS, ROLLBACKS_FOUND, ROLLBACKS_REMOVED, ROLLBACKS_REMAIN }

  public Rollbacks findCalendarRollbacks(boolean fixRollbacks, boolean showWarnings)
  {
    boolean   hasRollbacks = false;
    boolean   warningShown = false;

    for (int i = 1; i < size(); ++i) {
      TzTransition    prev = get(i - 1);
      TzTransition    curr = get(i);
      LocalDateTime   before = LocalDateTime.ofEpochSecond(curr.time - 1, 0, ZoneOffset.ofTotalSeconds(prev.utcOffset));
      LocalDate       beforeDate = before.toLocalDate();
      LocalDateTime   after = LocalDateTime.ofEpochSecond(curr.time, 0, ZoneOffset.ofTotalSeconds(curr.utcOffset));
      LocalDate       afterDate = after.toLocalDate();

      if (afterDate.compareTo(beforeDate) < 0) {
        hasRollbacks = true;

        LocalDateTime   turnbackTime = LocalDateTime.ofEpochSecond(curr.time, 0, ZoneOffset.ofTotalSeconds(prev.utcOffset));
        LocalDateTime   midnight = LocalDateTime.of(turnbackTime.getYear(), turnbackTime.getMonth(), turnbackTime.getDayOfMonth(), 0, 0);
        int             forayIntoNextDay = (int) midnight.until(turnbackTime, ChronoUnit.SECONDS);

        if (showWarnings && !warningShown) {
          int   forayMinutes = forayIntoNextDay / 60;
          int   foraySeconds = forayIntoNextDay % 60;
          System.out.print("* Warning -- " + zoneId + ": " + before.format(dateTimeFormat) + " rolls back to " + after.format(dateTimeFormat) +
            " (" + forayMinutes + " minute" + (foraySeconds > 0 ? ", " + foraySeconds + " second" : "") + " foray into next day)");
          warningShown = true;
        }

        if (fixRollbacks)
          curr.time -= forayIntoNextDay;
      }
    }

    boolean   stillHasRollbacks = false;

    if (hasRollbacks && fixRollbacks)
      stillHasRollbacks = (findCalendarRollbacks(false, false) == Rollbacks.ROLLBACKS_FOUND);

    if (warningShown) {
      if (fixRollbacks) {
        if (stillHasRollbacks)
          System.out.print(" *** NOT FIXED ***");
        else
          System.out.print(" * fixed *");
      }

      System.out.println();
    }

    if (!hasRollbacks)
      return Rollbacks.NO_ROLLBACKS;
    else if (!fixRollbacks)
      return Rollbacks.ROLLBACKS_FOUND;
    else if (stillHasRollbacks)
      return Rollbacks.ROLLBACKS_REMAIN;
    else
      return Rollbacks.ROLLBACKS_REMOVED;
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

        LocalDateTime   ldt = LocalDateTime.ofEpochSecond(tzt.time + 1, 0, ZoneOffset.ofTotalSeconds(tzt.utcOffset));

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

      LocalDateTime   ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(tzt.time + tzt.utcOffset), ZoneId.of("UTC"));

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
            nominalStdOffset = zone.getStandardOffset(lastTransitionTime).getTotalSeconds();

            if (zone.isDaylightSavings(lastTransitionTime)) {
              needDstOffset = false;
              nominalDstOffset = zone.getOffset(lastTransitionTime).getTotalSeconds() - nominalStdOffset;
            }
          }
          else
            break;
        }
      }

      if (needStdOffset || needDstOffset) {
        TimeZone  oldStyleZone = TimeZone.getTimeZone(zoneId);

        if (needStdOffset)
          nominalStdOffset = div(oldStyleZone.getRawOffset(), 1000);

        nominalDstOffset = oldStyleZone.getDSTSavings() / 1000;
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
      .append(' ').append(nominalDstOffset / 60).append(';');

    List<String>  uniqueOffsetList = new ArrayList<>();
    List<String>  offsetList = new ArrayList<>();

    for (TzTransition t : this) {
      String  offset = toBase60(t.utcOffset / 60.0) + "/" + toBase60(t.dstOffset / 60.0);

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

      sb.append(toBase60((t.time - lastTime) / 60.0)).append(' ');
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

        int   turnbackTime = (fallBackRule.atHour * 60 + fallBackRule.atMinute) * 60;

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

    if (sb.charAt(sb.length() - 1) == ';')
      sb.setLength(sb.length() - 1);

    return sb.toString();
  }

  private List<TzTransition> cloneTransitions()
  {
    List<TzTransition>  copy = new ArrayList<>(size());

    for (TzTransition tzt : this)
      copy.add((TzTransition) tzt.clone());

    return copy;
  }

  public boolean closelyMatchesJavaTransitions(TzTransitionList fromJava, boolean roundToMinutes)
  {
    // Java transition list is likely shorter since it is trimmed off before 1900, and it doesn't
    // contain some transitions like name-only changes. It is possible for Java to have a transition
    // lacking in the compiled transitions where Java includes a transition below the compiler's
    // optional one-minute round-off.
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

      // If rounding to minutes, allow for rounding to be to result in a one-minute difference.
      int   roundingAllowance = (roundToMinutes ? 60 : 0);

      if (abs(t.time - tj.time) > roundingAllowance ||
          abs(t.utcOffset      - tj.utcOffset) > roundingAllowance ||
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

  public boolean closelyMatchesZoneinfoTransitions(TzTransitionList fromZoneinfo, boolean roundToMinutes)
  {
    // ZoneInfo transition list might include transitions missing from our compiled transitions because
    // they're below the optional one-minute resolution.
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

      // If rounding to minutes, allow for rounding to be to result in a one-minute difference.
      int   roundingAllowance = (roundToMinutes ? 60 : 0);

      if (abs(t.time      - tzi.time) > roundingAllowance ||
          abs(t.utcOffset - tzi.utcOffset) > roundingAllowance ||
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

  public void dump(OutputStream out, boolean roundToMinutes) {
    //noinspection EmptyCatchBlock
    try {
      dump(new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, "UTF-8"))), roundToMinutes);
    }
    catch (UnsupportedEncodingException e) {}
  }

  public void dump(PrintWriter out, boolean roundToMinutes)
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
      int           padding = (roundToMinutes ? 5 : 7);

      out.println("____-__-__ __:__:__ �____" + (roundToMinutes ? "" : "__") + " �____ --> ____-__-__ __:__:__ " +
                  padRight(formatOffsetNotation(tzt.utcOffset), ' ', padding) + " " + formatOffsetNotation(tzt.dstOffset) +
                  (tzt.name != null ? " " + tzt.name : ""));

      for (int i = 1; i < size(); ++i) {
        TzTransition    prev = get(i - 1);
        ZoneOffset      prevOffset = ZoneOffset.ofTotalSeconds(prev.utcOffset);
        TzTransition    curr = get(i);
        ZoneOffset      currOffset = ZoneOffset.ofTotalSeconds(curr.utcOffset);
        LocalDateTime   prevDateTime = LocalDateTime.ofEpochSecond(curr.time - 1, 0, prevOffset);
        LocalDateTime   currDateTime = LocalDateTime.ofEpochSecond(curr.time, 0, currOffset);

        out.println(prevDateTime.format(dateTimeFormat) + " " + padRight(formatOffsetNotation(prev.utcOffset), ' ', padding) + " " + formatOffsetNotation(prev.dstOffset) + " --> " +
                    currDateTime.format(dateTimeFormat) + " " + padRight(formatOffsetNotation(curr.utcOffset), ' ', padding) + " " + formatOffsetNotation(curr.dstOffset) +
                    (curr.name != null ? " " + curr.name : "") + (curr.dstOffset != 0 ? "*" : ""));
      }
    }
  }
}
