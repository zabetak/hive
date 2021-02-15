---
title: Contribute
layout: default
---
<!---
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License. -->
  

This page describes the mechanics of how to contribute software to Apache Hive. 
For ideas about what you might contribute, please see open tickets in [Jira][HIVE_JIRA].

## Getting the Source Code
First of all, you need the Hive source code.

Get the source code on your local drive using git. Check [Understanding Hive Branches](#understanding-hive-branches)
section to find out which branch to use.

```
git clone https://github.com/apache/hive
```

## Setting Up Eclipse Development Environment (Optional)

Eclipse has a lot of advanced features for Java development, and it makes the life much easier for Hive developers.

[How do I import into eclipse?][ECLIPSE_INSTRUCTIONS]

## Becoming a Contributor
This checklist tells you how to create accounts and obtain permissions needed by Hive contributors. See the
[Hive website][HIVE_SITE] for additional information.

1. Create an Apache Software Foundation [JIRA account][JIRA_SIGNUP] if you do not already have one. 
   * The ASF JIRA system dashboard is [here][ASF_JIRA_DASHBOARD].
   * The Hive JIRA is [here][HIVE_JIRA].
2. To review patches for JIRA tickets, use the [Review Board][ASF_REVIEWS]. If you need an account, register
[here][ASF_REVIEWS_REGISTER]. For more information, check reviews [section](#review-process).
   * All Hive patches posted for review are listed [here][HIVE_REVIEWS].
   * Individual JIRA tickets provide a link to the issue on the review board when a review request has been made.
   * For simple reviews, you can just read the patch attached to the JIRA ticket and post a comment.
3. To contribute to the Hive wiki, follow the instructions in [About This Wiki][WIKI_PERMISSIONS].
4. To edit the Hive website, follow the instructions in [How to edit the website][WIKI_SITE_EDIT].
5. Join the [Hive mailing lists][MAILING_LISTS] to receive email about issues and discussions.

## Understanding Hive Branches
Hive has a few "main lines", master and branch-X.

All new feature work and bug fixes in Hive are contributed to the master branch. Releases are done from branch-X. 
Major versions like 2.x versions are not necessarily backwards compatible with 1.x versions.
Backwards compatibility will be accepted on branch-1.

In addition to these main lines Hive has two types of branches, release branches and feature branches.

Release branches are made from branch-1 (for 1.x) or master (for 2.x) when the community is preparing a Hive release.
Release branches match the number of the release (e.g., branch-1.2 for Hive 1.2).
For patch releases the branch is made from the existing release branch (to avoid picking up new features from the main
line). For example, if a 1.2.1 release was being made branch-1.2.1 would be made from the tip of branch-1.2.
Once a release branch has been made, inclusion of additional patches on that branch is at the discretion of the release
manager. After a release has been made from a branch, additional bug fixes can still be applied to that branch in
anticipation of the next patch release. Any bug fix applied to a release branch must first be applied to master
(and branch-1 if applicable).

Feature branches are used to develop new features without destabilizing the rest of Hive. The intent of a feature branch
is that it will be merged back into master once the feature has stabilized.

For general information about Hive branches, see [Hive Versions and Branches][HIVE_VERSIONS].

## Review Process

TODO

[HIVE_JIRA]: https://issues.apache.org/jira/browse/HIVE
[HIVE_SITE]: http://hive.apache.org/
[JIRA_SIGNUP]: https://issues.apache.org/jira/secure/Signup!default.jspa
[ASF_JIRA_DASHBOARD]: https://issues.apache.org/jira/secure/Dashboard.jspa
[ASF_REVIEWS]: https://reviews.apache.org/
[ASF_REVIEWS_REGISTER]: https://reviews.apache.org/account/register/
[HIVE_REVIEWS]: https://reviews.apache.org/dashboard/?view=to-group&group=hive
[MAILING_LISTS]: http://hive.apache.org/mailing_lists.html
[HIVE_VERSIONS]: https://cwiki.apache.org/confluence/display/Hive/Home#Home-HiveVersionsandBranches
[ECLIPSE_INSTRUCTIONS]: https://cwiki.apache.org/confluence/display/Hive/HiveDeveloperFAQ#HiveDeveloperFAQ-HowdoIimportintoEclipse?
[WIKI_PERMISSIONS]: https://cwiki.apache.org/confluence/display/Hive/AboutThisWiki#AboutThisWiki-Howtogetpermissiontoedit
[WIKI_SITE_EDIT]: https://cwiki.apache.org/confluence/display/Hive/How+to+edit+the+website