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
import java.util.*;

import static java.lang.Math.*;
import static org.shetline.timezones.TzUtil.*;


public class TzCompiler
{
  private IanaZonesAndRulesParser   parser;

  public TzCompiler(IanaZonesAndRulesParser parser)
  {
    this.parser = parser;
  }

  public Map<String, TzTransitionList> compileAll(int minYear, int maxYear)
  {
    Map<String, TzTransitionList>   compiledZones = new HashMap<>();

    for (String zoneId : parser.getZoneIds())
      compiledZones.put(zoneId, compile(zoneId, minYear, maxYear));

    return compiledZones;
  }

  public TzTransitionList compile(String zoneId, int minYear, int maxYear)
  {
    TzTransitionList        transitions = new TzTransitionList(zoneId);
    ZoneProcessingContext   zpc = new ZoneProcessingContext();
    IanaZone                zone = parser.getZone(zoneId);

    zpc.zoneId = zoneId;
    zpc.lastUtcOffset = 0;
    zpc.lastUntil = MIN_JS_SAFE_INTEGER;
    zpc.lastUntilType = CLOCK_TYPE_UTC;
    zpc.format = null;

    transitions.setLastZoneRec(zone.get(zone.size() - 1));

    for (IanaZoneRecord zoneRec : zone) {
      int   dstOffset = 0;

      if (zoneRec.rules != null && zoneRec.rules.indexOf(':') >= 0)
        dstOffset = parseOffsetTime(zoneRec.rules, true);

      zpc.utcOffset = zoneRec.gmtOffset;
      zpc.until = zoneRec.until;
      zpc.untilType = zoneRec.untilType;
      zpc.format = zoneRec.format;

      if (zoneRec.rules == null || zoneRec.rules.indexOf(':') >= 0) {
        String  name = createDisplayName(zoneRec.format, "?", dstOffset != 0);

        transitions.add(new TzTransition(zpc.lastUntil, zoneRec.gmtOffset + dstOffset, dstOffset, name));

        if (zoneRec.untilType == CLOCK_TYPE_WALL)
          zpc.until -= dstOffset;
      }
      else {
        applyRules(zoneRec.rules, transitions, zpc, minYear, maxYear);
      }

      zpc.lastUtcOffset = zpc.utcOffset;
      zpc.lastUntil = zpc.until;
      zpc.lastUntilType = zpc.untilType;

      if (zpc.until < MAX_JS_SAFE_INTEGER / 2) {
        LocalDateTime   ldt = LocalDateTime.ofEpochSecond(zpc.until, 0, ZoneOffset.ofTotalSeconds(zpc.utcOffset));

        if (ldt.getYear() > maxYear)
          break;
      }
    }

    transitions.removeDuplicateTransitions();
    transitions.trim(minYear, maxYear);

    return transitions;
  }

  private void applyRules(String rulesName, TzTransitionList transitions, ZoneProcessingContext zpc, int minYear, int maxYear)
  {
    TzRuleSet   ruleSet = parser.getRuleSet(rulesName);
    long        minTime = zpc.lastUntil;
    String      firstStdLetters = "?";
    String      fallbackStdLetters = "?";

    ZoneOffset  zoneOffset = ZoneOffset.ofTotalSeconds(zpc.utcOffset);
    ZoneOffset  lastZoneOffset = ZoneOffset.ofTotalSeconds(zpc.lastUtcOffset);
    ZoneOffset  utc = ZoneOffset.ofHours(0);
    int         lastDst = 0;
    int         highYear;

    if (transitions.size() > 0)
      lastDst = transitions.get(transitions.size() - 1).dstOffset;

    if (zpc.until >= MAX_JS_SAFE_INTEGER)
      highYear = 9999;
    else
      highYear = LocalDateTime.ofEpochSecond(zpc.until, 0, zoneOffset).getYear();

    TzTransitionList  newTransitions = new TzTransitionList();

    for (TzRule rule : ruleSet) {
      if (rule.startYear <= min(highYear, rule.endYear)) {
        for (int year = max(rule.startYear, 1800); year <= min(highYear, rule.endYear) && year <= maxYear; ++year) {
          int   ldtDate;
          int   ldtMonth = rule.month;
          int   ldtYear = year;

          if (rule.dayOfWeek >= 0 && rule.dayOfMonth > 0) {
            ldtDate = getDayOnOrAfter(year, ldtMonth, rule.dayOfWeek, rule.dayOfMonth);

            if (ldtDate <= 0) {
              int[]   ymd = getDateFromDayNumber(getDayNumber(ldtYear, ldtMonth, rule.dayOfMonth - ldtDate));

              ldtYear = ymd[0];
              ldtMonth = ymd[1];
              ldtDate = ymd[2];
            }
          }
          else if (rule.dayOfWeek >= 0 && rule.dayOfMonth < 0) {
            ldtDate = getDayOnOrBefore(year, ldtMonth, rule.dayOfWeek, -rule.dayOfMonth);

            if (ldtDate <= 0) {
              int[]   ymd = getDateFromDayNumber(getDayNumber(ldtYear, ldtMonth, rule.dayOfMonth + ldtDate));

              ldtYear = ymd[0];
              ldtMonth = ymd[1];
              ldtDate = ymd[2];
            }
          }
          else if (rule.dayOfWeek >= 0)
            ldtDate = getDateOfNthWeekdayOfMonth(year, ldtMonth, rule.dayOfWeek, LAST);
          else
            ldtDate = rule.dayOfMonth;

          LocalDateTime   ldt = LocalDateTime.of(ldtYear, ldtMonth, ldtDate, min(rule.atHour, 23), rule.atMinute);

          if (rule.atHour == 24)
            ldt = ldt.plus(1, ChronoUnit.HOURS);

          long  epochSecond = ldt.toEpochSecond(rule.atType == CLOCK_TYPE_UTC ? utc : zoneOffset);
          long  altEpochSecond = ldt.toEpochSecond(rule.atType == CLOCK_TYPE_UTC ? utc : lastZoneOffset) -
                  (rule.atType == CLOCK_TYPE_WALL ? lastDst : 0);

          if (altEpochSecond == minTime)
            epochSecond = minTime;

          String        name = createDisplayName(zpc.format, rule.letters, rule.save != 0);
          TzTransition  tzt = new TzTransition(epochSecond, zpc.utcOffset + rule.save, rule.save, name, rule);

          newTransitions.add(tzt);
        }
      }
    }

    // Transition times aren't exact yet (not adjusted for DST), but are accurate enough for sorting.
    newTransitions.sort((t1, t2) -> (int) TzUtil.signum(t1.time - t2.time));

    TzTransition  lastTransitionBeforeMinTime = null;
    boolean       addLeadingTransition = true;

    // Adjust wall time for DST where needed.
    for (int i = 1; i < newTransitions.size(); ++i) {
      TzTransition  prev = newTransitions.get(i - 1);
      TzTransition  curr = newTransitions.get(i);

      if (curr.rule.atType == CLOCK_TYPE_WALL)
        curr.time -= prev.rule.save;
    }

    for (int i = 0; i < newTransitions.size(); ++i) {
      TzTransition  tzt = newTransitions.get(i);
      TzRule        lastRule = (i < 1 ? null : newTransitions.get(i - 1).rule);
      long          maxTime = zpc.until - (lastRule != null && zpc.untilType == CLOCK_TYPE_WALL ? lastRule.save : 0);

      int   year = LocalDateTime.ofEpochSecond(tzt.time, 0, utc).getYear();

      if (minTime <= tzt.time && tzt.time < maxTime && minYear <= year && year <= maxYear) {
        if ("?".equals(firstStdLetters) && tzt.dstOffset == 0)
          firstStdLetters = tzt.rule.letters;

        if (tzt.time == minTime)
          addLeadingTransition = false;
      }
      else {
        newTransitions.remove(i--);

        // Find the last rule that was in effect before or at the time these rules were invoked.
        if (tzt.time < minTime && (lastTransitionBeforeMinTime == null || lastTransitionBeforeMinTime.time < tzt.time))
          lastTransitionBeforeMinTime = tzt;

        if ((tzt.time < minTime || "?".equals(fallbackStdLetters)) && tzt.dstOffset == 0)
          fallbackStdLetters = tzt.rule.letters;
      }
    }

    if (addLeadingTransition) {
      String  name;
      int     dstOffset = 0;
      TzRule  rule = null;

      if (lastTransitionBeforeMinTime != null) {
        rule = lastTransitionBeforeMinTime.rule;
        dstOffset = rule.save;
        name = createDisplayName(zpc.format, lastTransitionBeforeMinTime.rule.letters, dstOffset != 0);
      }
      else {
        String  letters = (firstStdLetters.equals("?") ? fallbackStdLetters : firstStdLetters);

        name = createDisplayName(zpc.format, letters, false);
      }

      newTransitions.add(0, new TzTransition(minTime, zpc.utcOffset + dstOffset, dstOffset, name, rule));
    }

    transitions.addAll(newTransitions);

    if (zpc.untilType == CLOCK_TYPE_WALL && transitions.size() > 0) {
      TzTransition  tzt = transitions.get(transitions.size() - 1);

      if (tzt.rule != null && zpc.until != MAX_JS_SAFE_INTEGER)
        zpc.until -= tzt.rule.save;
    }
  }

  private static String createDisplayName(String format, String letters, boolean isDst)
  {
    String  name;
    int     pos = format.indexOf("%s");

    if (pos >= 0) {
      if ("?".equals(letters))
        System.err.println("*** Error: unresolved time zone name " + format + (isDst ? ", DST" : ""));

      name = format.substring(0, pos) + letters + format.substring(pos + 2);
    }
    else {
      pos = format.indexOf("/");

      if (pos >= 0)
        name = (isDst? format.substring(pos + 1) : format.substring(0, pos));
      else
        name = format;
    }

    if (name.startsWith("+") || name.startsWith("-"))
      return null;
    else
      return name;
  }

  private static class ZoneProcessingContext
  {
    public String   zoneId;
    public int      lastUtcOffset;
    public long     lastUntil;
    public int      lastUntilType;
    public int      utcOffset;
    public long     until;
    public int      untilType;
    public String   format;
  }
}
