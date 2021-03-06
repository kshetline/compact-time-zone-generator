/*
  Copyright � 2018 Kerry Shetline, kerry@shetline.com

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
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.*;

import static org.shetline.timezones.TzUtil.*;


public class IanaZonesAndRulesParser
{
  private final Map<String, IanaZone>   zoneMap = new HashMap<>();
  private final Map<String, String>     zoneAliases = new HashMap<>();
  private final Map<String, TzRuleSet>  ruleSetMap = new HashMap<>();

  private boolean   roundToMinutes = false;
  private boolean   printProgress = false;
  private int       lineNo;

  public static final String    DEFAULT_URL = "https://www.iana.org/time-zones/repository/tzdata-latest.tar.gz";
  private static final String   URL_TEMPLATE_FOR_VERSION = "https://data.iana.org/time-zones/releases/tzdata{version}.tar.gz";
  private static final String[] TZ_SOURCE_FILES = {"africa", "antarctica", "asia", "australasia", "europe", "northamerica",
                                                   "pacificnew", "southamerica", "backward", "etcetera", "systemv"};

  public IanaZonesAndRulesParser()
  {
  }

  public IanaZonesAndRulesParser(boolean roundToMinutes, boolean printProgress)
  {
    this.roundToMinutes = roundToMinutes;
    this.printProgress = printProgress;
  }

  public String parseFromOnline(boolean includeSystemV) throws IOException, IanaParserException
  {
    return parseFromOnline(null, includeSystemV);
  }

  public String parseFromOnline(String urlOrVersion, boolean includeSystemV) throws IOException, IanaParserException
  {
    String  url;

    if (urlOrVersion == null)
      url = DEFAULT_URL;
    else if (urlOrVersion.contains(":"))
      url = urlOrVersion;
    else
      url = URL_TEMPLATE_FOR_VERSION.replace("{version}", urlOrVersion);

    URLConnection   conn = new URL(url).openConnection();

    return parseArchive(conn.getInputStream(), includeSystemV);
  }

  public String parseArchive(InputStream archiveIn, boolean includeSystemV) throws IOException, IanaParserException
  {
    InputStream               in = new BufferedInputStream(new GZIPInputStream(archiveIn));
    TarArchiveInputStream     tarIn = new TarArchiveInputStream(in);
    TarArchiveEntry           entry;
    Map<String, InputStream>  sources = new HashMap<>();
    String                    tzVersion = null;
    List<String>              tzSources = new ArrayList<>();

    while ((entry = tarIn.getNextTarEntry()) != null) {
      String  sourceName = entry.getName();

      if (contains(TZ_SOURCE_FILES, sourceName) || "version".equals(sourceName)) {
        byte[]  fileContent = new byte[(int) entry.getSize()];
        int     bytesRead = tarIn.read(fileContent);

        if (bytesRead != fileContent.length)
          System.err.println("*** Error reading " + sourceName + ": " + bytesRead + " != " + fileContent.length);

        if ("version".equals(sourceName)) {
          tzVersion = new String(fileContent, StandardCharsets.UTF_8).trim();

          if (printProgress)
            System.out.println("tz database version: " + tzVersion);
        }
        else {
          tzSources.add(sourceName);

          // Uncomment the commented-out time zones in the systemv file
          if ("systemv".equals(sourceName) && includeSystemV) {
            String  stringContent = new String(fileContent, StandardCharsets.UTF_8);

            stringContent = stringContent.replaceAll("## Zone", "Zone");
            fileContent = stringContent.getBytes(StandardCharsets.UTF_8);
          }

          if (printProgress)
            System.out.println("Extracting " + sourceName);

          sources.put(sourceName, new ByteArrayInputStream(fileContent));
        }
      }
    }

    if (printProgress)
      System.out.println("Parsing tz database sources");

    parseSources(tzSources.toArray(new String[0]), sources);

    // Add aliases if needed for legacy time zones. Not all substitutes exactly duplicate their originals.
    if (includeSystemV && !tzSources.contains("systemv")) {
      zoneAliases.put("SystemV/AST4", getRootZone("America/Anguilla"));
      zoneAliases.put("SystemV/AST4ADT", getRootZone("America/Goose_Bay"));
      zoneAliases.put("SystemV/CST6", getRootZone("America/Belize"));
      zoneAliases.put("SystemV/CST6CDT", getRootZone("America/Chicago"));
      zoneAliases.put("SystemV/EST5", getRootZone("America/Atikokan"));
      zoneAliases.put("SystemV/EST5EDT", getRootZone("America/New_York"));
      zoneAliases.put("SystemV/HST10", getRootZone("HST"));
      zoneAliases.put("SystemV/MST7", getRootZone("America/Creston"));
      zoneAliases.put("SystemV/MST7MDT", getRootZone("America/Boise"));
      zoneAliases.put("SystemV/PST8", getRootZone("Etc/GMT+8"));
      zoneAliases.put("SystemV/PST8PDT", getRootZone("America/Los_Angeles"));
      zoneAliases.put("SystemV/YST9", getRootZone("Etc/GMT+8"));
      zoneAliases.put("SystemV/YST9YDT", getRootZone("America/Anchorage"));
    }

    if (!tzSources.contains("pacificnew"))
      zoneAliases.put("US/Pacific-New", getRootZone("America/Los_Angeles"));

    return tzVersion;
  }

  private String getRootZone(String zoneId)
  {
    while (zoneAliases.containsKey(zoneId))
      zoneId = zoneAliases.get(zoneId);

    return zoneId;
  }

  public void parseSources(String[] sourceNames, Map<String, InputStream> inputStreams) throws IanaParserException
  {
    for (String sourceName : sourceNames) {
      try {
        InputStream   in = inputStreams.get(sourceName);

        if (in == null)
          throw new IOException("File not found");
        else {
          try {
            parseSource(sourceName, in);
          }
          catch (RuntimeException e) {
            throw new IanaParserException(lineNo, sourceName, e.getMessage());
          }
        }
      }
      catch (IOException e) {
        throw new IanaParserException(0, sourceName, "Failed reading \"" + sourceName + "\": " + e.getMessage());
      }
    }

    // Remove aliases for anything that actually has its own defined zone.
    for (String zoneId : zoneMap.keySet()) {
      if (zoneAliases.containsKey(zoneId))
        zoneAliases.remove(zoneId);
    }

    // Make sure remaining aliases point to a defined zone.
    for (String zoneId : zoneAliases.keySet()) {
      String  original = zoneAliases.get(zoneId);

      if (!zoneMap.containsKey(original))
        throw new IanaParserException(0, null, zoneId + " is mapped to unknown time zone " + original);
    }
  }

  private void parseSource(String sourceName, InputStream source) throws IOException, IanaParserException
  {
    BufferedReader    in = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
    String            line;
    IanaZone          zone = null;
    IanaZoneRecord    zoneRec;
    String            zoneId = null;

    lineNo = 0;

    while ((line = readLine(in)) != null) {
      zoneRec = null;

      if (line.startsWith("Rule")) {
        TzRule          rule = TzRule.parseRule(line);
        String          ruleName = rule.name;
        TzRuleSet       ruleSet = ruleSetMap.get(ruleName);

        if (ruleSet == null) {
          ruleSet = new TzRuleSet(ruleName);
          ruleSetMap.put(ruleName, ruleSet);
        }

        ruleSet.add(rule);
      }
      else if (line.startsWith("Link")) {
        String[]  parts = line.split("\\s+");

        zoneAliases.put(parts[2], parts[1]);
      }
      else if (line.startsWith("Zone")) {
        if (zone != null)
          throw new IanaParserException(lineNo, sourceName, "Zone " + zoneId + " was not properly terminated");

        StringBuilder   sb = new StringBuilder();

        zoneRec = IanaZoneRecord.parseZoneRecord(line, sb, roundToMinutes);
        zoneId = sb.toString();
        zone = new IanaZone(zoneId);
      }
      else if (zone != null)
        zoneRec = IanaZoneRecord.parseZoneRecord(line, null, roundToMinutes);

      if (zoneRec != null) {
        zone.add(zoneRec);

        if (zoneRec.until == MAX_JS_SAFE_INTEGER) {
          zoneMap.put(zoneId, zone);
          zone = null;
        }
      }
    }

    in.close();
  }

  public List<String> getZoneIds()
  {
    List<String>  zoneIds = new ArrayList<>();

    zoneIds.addAll(zoneMap.keySet());
    zoneIds = zoneIds.stream().map(zone -> "*" + zone).collect(Collectors.toList());
    zoneIds.addAll(zoneAliases.keySet());

    Collections.sort(zoneIds);
    zoneIds = zoneIds.stream().map(zone -> zone.replace("*", "")).collect(Collectors.toList());

    return zoneIds;
  }

  public String getAliasFor(String zoneId)
  {
    return zoneAliases.get(zoneId);
  }

  public IanaZone getZone(String zoneId)
  {
    if (zoneAliases.containsKey(zoneId))
      zoneId = zoneAliases.get(zoneId);

    return zoneMap.get(zoneId);
  }

  public TzRuleSet getRuleSet(String rulesName)
  {
    return ruleSetMap.get(rulesName);
  }

  private String readLine(BufferedReader in) throws IOException
  {
    String  line;

    do {
      do {
        line = in.readLine();
        ++lineNo;
      } while (line != null && (line.startsWith("#") || line.length() == 0));

      if (line != null) {
        int pos = line.indexOf("#");

        if (pos > 0)
          line = line.substring(0, pos);

        line = rtrim(line);
      }
    } while (line != null && line.length() == 0);

    return line;
  }
}
