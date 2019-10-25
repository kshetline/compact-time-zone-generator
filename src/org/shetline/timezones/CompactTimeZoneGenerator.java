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
import java.time.zone.ZoneRulesProvider;
import java.util.*;
import java.util.regex.*;

import static org.shetline.timezones.TzUtil.join;
import static org.shetline.timezones.TzUtil.to_int;


public class CompactTimeZoneGenerator
{
  private static final int DEFAULT_MIN_YEAR = 1900;
  private static final int DEFAULT_MAX_YEAR = 2050;

  private static final String DEFAULT_JS_OUTPUT_FILE   = "timezones.js";
  private static final String DEFAULT_JSON_OUTPUT_FILE = "timezones.json";
  private static final String DEFAULT_TEXT_OUTPUT_FILE = "timezones.txt";

  private static final Pattern skippedZones = Pattern.compile("America/Indianapolis|America/Knox_IN|Asia/Riyadh\\d\\d");

  private static final Pattern extendedRegions = Pattern.compile("(America/Argentina|America/Indiana)/(.+)");

  private static final Pattern skippedRegions = Pattern.compile("Etc|GB|GB-Eire|GMT0|NZ|NZ-CHAT|SystemV|W-SU|Zulu|" +
          "Mideast|[A-Z]{3}(\\d[A-Z]{3})?");

  private static final Pattern miscUnique = Pattern.compile("CST6CDT|EET|EST5EDT|MST7MDT|PST8PDT|SystemV/AST4ADT|" +
          "SystemV/CST6CDT|SystemV/EST5EDT|SystemV/MST7MDT|" +
          "SystemV/PST8PDT|SystemV/YST9YDT|WET");

  public static void main(String[] args)
  {
    String        tzVersion = "unknown";
    String        singleZone = null;
    String        urlOrVersion = null;
    int           minYear = DEFAULT_MIN_YEAR;
    int           maxYear = DEFAULT_MAX_YEAR;
    boolean       filtered = false;
    boolean       supplementFromJava = false;
    boolean       json = false;
    boolean       showWarnings = true;
    boolean       fixCalendarRollbacks = false;
    boolean       toStdOut = false;
    boolean       showTable = false;
    boolean       includeSystemV = false;
    boolean       roundToMinutes = false;
    String        outFileName = null;
    String        zoneInfoPath = null;
    final String  simpleFlags = "5fhJjmqrtv";

    for (int i = 0; i < args.length; ++i) {
      String    arg = args[i];
      boolean   hasMore = (i < args.length - 1);

      if (arg.startsWith("-") && arg.length() > 2) {
        String  flag = arg.substring(1, 2);

        if (simpleFlags.contains(flag)) {
          args[i--] = "-" + arg.substring(2);
          arg = "-" + flag;
        }
      }

      if ("-l".equals(arg) && hasMore)
        urlOrVersion = args[++i];
      else if ("-y".equals(arg) && hasMore) {
        String[]  parts = (" " + args[++i] + " ").split(",");

        if (parts.length == 1)
          minYear = maxYear = to_int(parts[0]);
        else if (parts.length == 2) {
          minYear = to_int(parts[0], DEFAULT_MIN_YEAR);
          maxYear = to_int(parts[1], DEFAULT_MAX_YEAR);
        }
      }
      else if ("-s".equals(arg) && hasMore) {
        singleZone = args[++i];
        filtered = false;
      }
      else if ("-z".equals(arg) && hasMore)
        zoneInfoPath = args[++i];
      else if ("-f".equals(arg))
        filtered = true;
      else if ("-h".equals(arg) || "--help".equals(arg)) {
        System.out.println("Usage: java -jar ctzgenerator.jar [options] [output_file_name]");
        System.out.println("options:");
        System.out.println("        -              Send output to stdout instead of a file.");
        System.out.println("        -5, --systemv  Include the SystemV timezones from the systemv file by uncommenting");
        System.out.println("                       the commented-out zone descriptions.");
        System.out.println("        -f             Filter out Etc/GMTxxxx and other time zones that are either redundant");
        System.out.println("                       or covered by options for creating fixed-offset time zones.");
        System.out.println("        -h, --help     Display this help.");
        System.out.println("        -J, --json     Output JSON instead of JavaScript.");
        System.out.println("        -j             Use Java's built-in java.time time zones to supplement time zone");
        System.out.println("                       descriptions in the IANA source files.");
        System.out.println("        -l             <URL or version number, such as \"2018c\", to parse and compile>");
        System.out.println("                       Default: " + IanaZonesAndRulesParser.DEFAULT_URL);
        System.out.println("        -m             Round all zone offsets to whole minutes.");
        System.out.println("        -q             Display fewer warning messages.");
        System.out.println("        -r             Remove \"calendar rollbacks\" from time zone transitions -- that is,");
        System.out.println("                       modify time zone data to prevent situations where the calendar date");
        System.out.println("                       goes backwards as well as the hour and/or minute of the day.");
        System.out.println("        -s             <zone_id> Zone ID for a single time zone to be rendered.");
        System.out.println("        -t             Generate more human-readable transitions table instead of using the");
        System.out.println("                       compact notation.");
        System.out.println("        -v, --version  Display the version of this tool.");
        System.out.println("        -y             <min_year,max_year> Year range for explicit time zone transitions.");
        System.out.println("                       Default: " + DEFAULT_MIN_YEAR + "," + DEFAULT_MAX_YEAR);
        System.out.println("        -z             <path_to_zoneinfo_directory> Validate this tool's output against");
        System.out.println("                       output from the standard zic tool stored in the given directory.");
        System.out.println("                       Validation is done before applying the -r option.");
        System.exit(0);
      }
      else if ("-J".equals(arg) || "--json".equals(arg))
        json = true;
      else if ("-j".equals(arg))
        supplementFromJava = true;
      else if ("-m".equals(arg))
        roundToMinutes = true;
      else if ("-q".equals(arg))
        showWarnings = false;
      else if ("-r".equals(arg))
        fixCalendarRollbacks = true;
      else if ("-t".equals(arg))
        showTable = true;
      else if ("-5".equals(arg) || "--systemv".equals(arg))
        includeSystemV = true;
      else if ("-v".equals(arg) || "--version".equals(arg)) {
        System.out.println("Version 1.0.0");
        System.exit(0);
      }
      else if ("-".equals(arg))
        toStdOut = true;
      else if (!arg.startsWith("-"))
        outFileName = arg;
    }

    if (outFileName == null)
      outFileName = (showTable ? DEFAULT_TEXT_OUTPUT_FILE : (json ? DEFAULT_JSON_OUTPUT_FILE : DEFAULT_JS_OUTPUT_FILE));

    IanaZonesAndRulesParser         parser = new IanaZonesAndRulesParser(roundToMinutes, true);
    Map<String, TzTransitionList>   compiledZones;

    try {
      tzVersion = parser.parseFromOnline(urlOrVersion, includeSystemV);
    }
    catch (IOException e) {
      System.err.println(e.getMessage());
    }
    catch (IanaParserException e) {
      System.err.print(e.getMessage());

      if (e.getSource() != null)
        System.err.print(" (" + e.getSource() + ")");

      if (e.getLineNo() != 0)
        System.err.print(" (line " + e.getLineNo() + ")");

      System.err.println();
      System.exit(-1);
    }

    System.out.println("Compiling time zones");

    TzCompiler  compiler = new TzCompiler(parser);

    compiledZones = compiler.compileAll(minYear, maxYear);

    List<String>  savedZones = new ArrayList<>();
    Set<String>   zones = new HashSet<>(parser.getZoneIds());

    // Merge collection of time zones IDs known to Java with those parsed from the tz database.
    if (supplementFromJava)
      zones.addAll(ZoneRulesProvider.getAvailableZoneIds());

    for (String zoneId : zones) {
      if (filtered && skippedZones.matcher(zoneId).matches())
        continue;

      if (singleZone != null && !zoneId.equals(singleZone))
        continue;

      String    region;
      String    locale;
      Matcher matcher = extendedRegions.matcher(zoneId);

      if (matcher.matches()) {
        region = matcher.group(1);
        locale = matcher.group(2);
      }
      else {
        int   pos = zoneId.indexOf('/');

        region = (pos < 0 ? zoneId : zoneId.substring(0, pos));
        locale = (pos < 0 ? null : zoneId.substring(pos + 1));
      }

      if (filtered && (locale == null || skippedRegions.matcher(region).matches()) && !miscUnique.matcher(zoneId).matches())
        continue;

      savedZones.add(zoneId);
    }

    Collections.sort(savedZones);

    if (singleZone != null && !savedZones.contains(singleZone)) {
      System.err.println("*** Unknown time zone: " + singleZone);
      System.exit(-1);
    }

    Map<String, String>   zonesByCompactTable = new HashMap<>();
    Map<String, String>   compactTablesByZone = new HashMap<>();
    Map<String, TzTransitionList>
                          transitionsByZone = new HashMap<>();
    int                   unique = savedZones.size();
    Map<String, String>   duplicates = new HashMap<>();

    System.out.println("Creating compact transition tables" +
                       (zoneInfoPath != null ? " / validating with ZoneInfo" : "") +
                       (showWarnings || fixCalendarRollbacks ? " / checking for calendar rollbacks" : ""));

    List<String>  validatedWithJava = new ArrayList<>();

    for (String zoneId : savedZones) {
      TzTransitionList  transitions = (compiledZones != null ? compiledZones.get(zoneId) : null);
      boolean           fromJava = false;

      if (transitions == null) {
        if (showWarnings && supplementFromJava)
          System.out.println("* Warning: " + zoneId + " will be obtained from Java");

        transitions = TzTransitionList.getTzTransitionListJavaTime(zoneId, minYear, maxYear, roundToMinutes);
        fromJava = true;
      }

      if (zoneInfoPath != null && !fromJava) {
        TzTransitionList  zoneinfoTransitions = TzTransitionList.getZoneTransitionsFromZoneinfo(zoneInfoPath, zoneId, roundToMinutes);

        if (zoneinfoTransitions == null) {
          TzTransitionList  javaTransitions = TzTransitionList.getTzTransitionListJavaTime(zoneId, minYear, maxYear, roundToMinutes);

          if (javaTransitions == null)
            System.out.println("* Warning: " + zoneId + " could not be read from zoneinfo directory for validation");
          else {
            validatedWithJava.add(zoneId);

            if (!transitions.closelyMatchesJavaTransitions(javaTransitions, roundToMinutes))
              System.err.println("*** Compiled " + zoneId + " does not match java.time version");
          }
        }
        else {
          zoneinfoTransitions.trim(minYear, maxYear);

          if (!transitions.closelyMatchesZoneinfoTransitions(zoneinfoTransitions, roundToMinutes))
            System.err.println("*** Compiled " + zoneId + " does not match ZoneInfo version");
        }
      }

      if ((showWarnings || fixCalendarRollbacks) &&
          transitions.findCalendarRollbacks(fixCalendarRollbacks, showWarnings) == TzTransitionList.Rollbacks.ROLLBACKS_REMAIN)
        System.err.println("*** Failed to fix calendar rollbacks in " + zoneId);

      String  ctt = transitions.createCompactTransitionTable(fixCalendarRollbacks);

      if (zonesByCompactTable.containsKey(ctt)) {
        --unique;
        duplicates.put(zoneId, zonesByCompactTable.get(ctt));
      }
      else {
        zonesByCompactTable.put(ctt, zoneId);
        compactTablesByZone.put(zoneId, ctt);
        transitionsByZone.put(zoneId, transitions);
      }
    }

    if (validatedWithJava.size() > 0)
      System.out.println("Note: " + join(validatedWithJava.toArray(), ", ") + " validated using java.time instead of ZoneInfo");

    List<String>  duplicateZones = new ArrayList<>(duplicates.keySet());

    Collections.sort(duplicateZones);

    System.out.println(zones.size() + " time zone IDs, " +
                       (savedZones.size() < zones.size() ? "filtered down to " + savedZones.size() + ", " : "") +
                       unique + " unique");

    List<String>  uniqueZones = new ArrayList<>(compactTablesByZone.keySet());

    Collections.sort(uniqueZones);

    System.out.println("Validating compact transition tables");

    for (String zoneId : uniqueZones) {
      String            table = compactTablesByZone.get(zoneId);
      TzTransitionList  transitions = TzTransitionList.parseCompactZoneTable(table);
      TzTransitionList  oldTransitions = transitionsByZone.get(zoneId);

      if (!transitions.transitionsMatch(oldTransitions))
        System.err.println("*** Compact table error: " + zoneId);
    }

    if (!toStdOut)
      System.out.println(showTable ? "Writing transition tables" : "Writing JavaScript time zone file");

    try {
      PrintWriter   out = (toStdOut ? new PrintWriter(System.out, true) : new PrintWriter(outFileName, "UTF-8"));

      if (showTable) {
        for (String zoneId : savedZones) {
          if (duplicateZones.contains(zoneId))
            zoneId = duplicates.get(zoneId);

          TzTransitionList  transitions = transitionsByZone.get(zoneId);

          transitions.dump(out, roundToMinutes);
          out.println();
          out.println();
        }
      }
      else {
        String  quote = (json ? "\"" : "'");
        String  comment = "tz database version: " + tzVersion + ", years " + minYear + "-" + maxYear;

        if (roundToMinutes)
          comment += ", rounded to nearest minute";

        if (filtered)
          comment += ", filtered";

        if (fixCalendarRollbacks)
          comment += ", calendar rollbacks eliminated";

        if (json)
          out.println("{");
        else
          out.println("  { // " + comment);

        boolean   firstLine = true;

        for (String zoneId : uniqueZones) {
          if (!firstLine)
            out.println(",");
          else
            firstLine = false;

          out.print("  " + quote + zoneId + quote + ": " + quote + compactTablesByZone.get(zoneId) + quote);
        }

        for (String zoneId : duplicateZones) {
          if (!firstLine)
            out.println(",");
          else
            firstLine = false;

          out.print("  " + quote + zoneId + quote + ": " + quote + duplicates.get(zoneId) + quote);
        }

        out.println();

        if (json)
          out.println("}");
        else
          out.println("  };");
      }

      out.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
