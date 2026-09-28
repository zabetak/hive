#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
FROM postgres:18-alpine

ADD https://github.com/thomasrebele/hive-postgres-metastore/releases/download/tpcds-30tb-histogram-1.0/metastore_tpcds30tb_with_histograms.raw_db.zstd /tmp/metastore_db.zstd
RUN echo '0423efc0cbc554611ac3bbf5e7919b050767bf96  /tmp/metastore_db.zstd' > /tmp/metastore_db.sha1; sha1sum -c /tmp/metastore_db.sha1
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod u+x /usr/local/bin/entrypoint.sh
COPY postgresql.conf /etc/postgresql/postgresql.conf
RUN chown postgres:postgres /tmp/metastore_db.zstd /etc/postgresql/postgresql.conf

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

CMD ["postgres", "-c", "config_file=/etc/postgresql/postgresql.conf"]

