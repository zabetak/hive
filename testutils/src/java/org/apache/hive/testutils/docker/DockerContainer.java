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
package org.apache.hive.testutils.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerContainer {
  private static final Logger LOG = LoggerFactory.getLogger(DockerContainer.class);
  private static final int MAX_STARTUP_WAIT = 5 * 60 * 1000;

  private final String imageName;
  private final String containerName;
  private final List<String> dockerAdditionalArgs;

  public DockerContainer(final String imageName, final String containerName, String... additionalArgs) {
    this.imageName = imageName;
    this.containerName = createContainerName(containerName);
    this.dockerAdditionalArgs = Arrays.asList(additionalArgs);
  }

  private String[] buildArray(String... strs) {
    return strs;
  }

  public static class ProcessResults {
    final String stdout;
    final String stderr;
    final int rc;

    public ProcessResults(String stdout, String stderr, int rc) {
      this.stdout = stdout;
      this.stderr = stderr;
      this.rc = rc;
    }
  }

  public void start() throws IOException, InterruptedException {
    runCmdAndPrintStreams(buildRmCmd(), 600);
    if (runCmdAndPrintStreams(buildRunCmd(), 600).rc != 0) {
      printDockerEvents();
      throw new RuntimeException("Unable to start docker container");
    }
    long startTime = System.currentTimeMillis();
    ProcessResults pr;
    do {
      Thread.sleep(1000);
      pr = runCmdAndPrintStreams(buildLogCmd(), 30);
      if (pr.rc != 0) {
        printDockerEvents();
        throw new RuntimeException("Failed to get docker logs");
      }
    } while (startTime + MAX_STARTUP_WAIT >= System.currentTimeMillis() && !isContainerReady(pr));
    if (startTime + MAX_STARTUP_WAIT < System.currentTimeMillis()) {
      printDockerEvents();
      throw new RuntimeException(
          String.format("Container initialization failed within %d seconds. Please check the hive logs.",
              MAX_STARTUP_WAIT / 1000));
    }
  }

  /**
   * Determine if the docker container is ready to use.
   *
   * @param pr output of docker logs command
   * @return true if ready, false otherwise
   */
  public boolean isContainerReady(ProcessResults pr) {
    return !pr.stdout.isEmpty();
  }

  public void stop() {
    if ("true".equalsIgnoreCase(System.getProperty("metastore.itest.no.stop.container"))) {
      LOG.warn("Not stopping container " + containerName + " at user request, please "
          + "be sure to shut it down before rerunning the test.");
      return;
    }
    try {
      if (runCmdAndPrintStreams(buildRmCmd(), 600).rc != 0) {
        throw new RuntimeException("Unable to remove docker container");
      }
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    }
  }

  private String createContainerName(String baseName) {
    String suffix = System.getenv("HIVE_TEST_DOCKER_CONTAINER_SUFFIX");
    if (suffix == null) {
      suffix = "";
    } else {
      suffix = "-" + suffix;
    }
    return String.format("hive-container-%s-%s", baseName, suffix);
  }

  private ProcessResults runCmd(String[] cmd, long secondsToWait) throws IOException, InterruptedException {
    LOG.info("Going to run: " + String.join(" ", cmd));
    Process proc = Runtime.getRuntime().exec(cmd);
    if (!proc.waitFor(Math.abs(secondsToWait), TimeUnit.SECONDS)) {
      throw new RuntimeException("Process " + cmd[0] + " failed to run in " + secondsToWait + " seconds");
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    final StringBuilder lines = new StringBuilder();
    reader.lines().forEach(s -> lines.append(s).append('\n'));

    reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
    final StringBuilder errLines = new StringBuilder();
    reader.lines().forEach(s -> errLines.append(s).append('\n'));
    LOG.info("Result lines#: {}(stdout);{}(stderr)", lines.length(), errLines.length());
    return new ProcessResults(lines.toString(), errLines.toString(), proc.exitValue());
  }

  private ProcessResults runCmdAndPrintStreams(String[] cmd, long secondsToWait)
      throws InterruptedException, IOException {
    ProcessResults results = runCmd(cmd, secondsToWait);
    LOG.info("Stdout from proc: " + results.stdout);
    LOG.info("Stderr from proc: " + results.stderr);
    return results;
  }

  private void printDockerEvents() {
    try {
      runCmdAndPrintStreams(new String[] { "docker", "events", "--since", "24h", "--until", "0s" }, 3);
    } catch (Exception e) {
      LOG.warn("A problem was encountered while attempting to retrieve Docker events (the system made an analytical"
          + " best effort to list the events to reveal the root cause). No further actions are necessary.", e);
    }
  }

  private String[] buildRunCmd() {
    List<String> cmd = new ArrayList<>(4 + dockerAdditionalArgs.size());
    cmd.add("docker");
    cmd.add("run");
    cmd.add("--rm");
    cmd.add("--name");
    cmd.add(containerName);
    cmd.addAll(dockerAdditionalArgs);
    cmd.add(imageName);
    return cmd.toArray(new String[cmd.size()]);
  }

  private String[] buildRmCmd() {
    return buildArray("docker", "rm", "-f", "-v", containerName);
  }

  private String[] buildLogCmd() {
    return buildArray("docker", "logs", containerName);
  }

  public String getHostAddress() {
    String hostAddress = System.getenv("HIVE_TEST_DOCKER_HOST");
    if (hostAddress != null) {
      return hostAddress;
    } else {
      return "localhost";
    }
  }
}
