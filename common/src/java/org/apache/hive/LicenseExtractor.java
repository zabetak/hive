/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class LicenseExtractor {
  public static void main(String[] args) throws IOException {
    Path root = Paths.get(".");
    if (args.length == 1) {
      root = Paths.get(args[0]);
    }
    Path out = Files.createTempDirectory("licenseAgg");
    try (Stream<Path> paths = Files.walk(root)) {

      Map<Path, List<Path>> jarPaths = paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".jar"))
          .collect(Collectors.groupingBy(Path::getFileName));

      for (Path jarName : jarPaths.keySet()) {
        Path dout = Files.createDirectory(out.resolve(jarName.getFileName().toString().replace(".jar", "")));
        System.out.println(jarName);
        System.out.println(dout);
        Path jarPath = jarPaths.get(jarName).get(0);
        try {
          final JarFile jf = new JarFile(jarPath.toFile());
          List<JarEntry> files = jf.stream().filter(
                  e -> e.getName().toUpperCase().contains("LICENSE") || e.getName().toLowerCase().contains("pom.xml")
                      || e.getName().toUpperCase().contains("NOTICE")).filter(e -> !e.isDirectory())
              .collect(Collectors.toList());
          for (JarEntry z : files) {
            byte[] content = readAll(jf, z);
            System.out.println(z.getName());
            Files.write(dout.resolve(fileName(z)), content);
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static String fileName(ZipEntry z) {
    int i = z.getName().lastIndexOf('/');
    return i > 0 ? z.getName().substring(i+1) : z.getName();
  }
  
  private static byte[] readAll(JarFile jar, ZipEntry entry) throws IOException {
    try (InputStream is = jar.getInputStream(entry)) {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      for (int length; (length = is.read(buffer)) != -1; ) {
        result.write(buffer, 0, length);
      }
      return result.toByteArray();
    }
  }
}
