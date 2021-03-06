/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.terracotta.connection;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class URLConfigUtil {

  private static final Pattern pattern = Pattern.compile("\\$\\{.+?\\}");

  /**
   * Parses the input URI for ${.*} patterns and expands individual pattern based on the system property
   * 
   * @input String. ${tc_active}
   * @return String. activeHost:9510
   */
  public static String translateSystemProperties(String urlConfig) {
    String workingUrl = urlConfig.trim();
    Set<String> properties = extractPropertyTokens(workingUrl);
    
    for (String token : properties) {
      String leftTrimmed = token.replaceAll("\\$\\{", "");
      String trimmedToken = leftTrimmed.replaceAll("\\}", "");
      String property = System.getProperty(trimmedToken);
      
      if (property != null) {
        String propertyWithQuotesProtected = Matcher.quoteReplacement(property);
        workingUrl = workingUrl.replaceAll("\\$\\{" + trimmedToken + "\\}", propertyWithQuotesProtected);
      }
    }
    return workingUrl;
  }

  /**
   * Extracts properties of the form ${...}
   * 
   * @param sourceString
   * @return a Set of properties
   */
  static Set<String> extractPropertyTokens(String sourceString) {
    Set<String> propertyTokens = new HashSet<String>();
    Matcher matcher = pattern.matcher(sourceString);
    while (matcher.find()) {
      String token = matcher.group();
      propertyTokens.add(token);
    }
    return propertyTokens;
  }

  public static String getUsername(String embeddedTcConfig) {
    final String translated = translateSystemProperties(embeddedTcConfig);
    final String[] split = translated.split(",");
    String username = null;
    for (String s : split) {
      final int index = s.indexOf('@');
      if (index != -1) {
        String tmpUsername = s.substring(0, index).trim();
        if (username != null && !username.equals(tmpUsername)) {
          throw new AssertionError(format("Invalid configuration: different username found in Terracotta connection URLs " +
                                          "- %s and %s",username, tmpUsername));
        }
        username = tmpUsername;
        try {
          username = URLDecoder.decode(username, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
          // cannot happen
        }
      }
    }
    return username;
  }

}
