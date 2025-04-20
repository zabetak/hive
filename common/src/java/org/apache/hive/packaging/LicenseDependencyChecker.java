/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hive.packaging;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Run with
 * {@code
 * mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java@check-licenses -Pdist
 * }
 */
public class LicenseDependencyChecker {

  public static void main(String[] args) throws Exception {
    String baseDirectory = args[0];
    File libDir = new File(baseDirectory + "lib");
    File licenseFile = new File(baseDirectory + "licenses.xml");

    if (!libDir.exists()) {
      System.err.println("Artifact directory " + baseDirectory + " not found.");
      System.exit(1);
    }
    if (!licenseFile.exists()) {
      System.err.println("License file " + licenseFile + " not found.");
      System.exit(1);
    }

    Set<String> jarFiles =
        Arrays.stream(Objects.requireNonNull(libDir.listFiles((dir, name) -> name.endsWith(".jar")))).map(File::getName)
            .collect(Collectors.toSet());

    Set<String> licenseEntries = licensedArtifacts(licenseFile);

    Set<String> unlicensedJars = jarFiles.stream().filter(file -> licenseEntries.stream().noneMatch(file::startsWith))
        .collect(Collectors.toSet());
    Set<String> superfluousLicenses =
        licenseEntries.stream().filter(license -> jarFiles.stream().noneMatch(f -> f.startsWith(license)))
            .collect(Collectors.toSet());

    if (!unlicensedJars.isEmpty()) {
      System.out.println("❌ Missing license entries for the following JARs:");
      unlicensedJars.forEach(System.out::println);
    }

    if (!superfluousLicenses.isEmpty()) {
      System.out.println("❌ Superfluous license entries in licenses.xml:");
      superfluousLicenses.forEach(System.out::println);
    }

  }

  private static Set<String> licensedArtifacts(File licenseXml) throws Exception {
    Set<String> licensed = new HashSet<>();
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(licenseXml);
    NodeList nodes = doc.getElementsByTagName("dependency");
    for (int i = 0; i < nodes.getLength(); i++) {
      NodeList dep = nodes.item(i).getChildNodes();
      String artifactId = "";
      String version = "";
      for (int j = 0; j < dep.getLength(); j++) {
        if (dep.item(j).getNodeName().equals("artifactId")) {
          artifactId = dep.item(j).getTextContent();
        }
        if (dep.item(j).getNodeName().equals("version")) {
          version = dep.item(j).getTextContent();
        }
      }
      licensed.add(artifactId + "-" + version);
    }
    return licensed;
  }
}
