# apache/logging-log4j2 Weekly Health Report

Generated: 2026-06-10

## Summary

- Open Issues: 159
- Open PRs: 42
- Critical Issues Count: 5
- Hidden Critical Count: 1
- Duplicate Clusters Count: 9
- Stale PRs Count: 18

## Critical Issues
1. #1666 - kafka appender: When kafka server is down, application can't start (Author: ldcsaa)
2. #2111 - Startup on Mac is extremely slow consistently. (Author: brr53)
3. #3282 - When logging exceptions, the exception stack traces include dynamically generated class names, which leads to a memory leak. (Author: Tonys-L)
4. #3569 - Memory Usage Increase in Log4j2 2.24.3 with Async Logging Due to RingBufferLogEvent (Author: shaonan666)
5. #4009 - Deadlock when use TimeoutBlockingWaitStrategy (Author: cs-xiao)

## Hidden Critical
1. #2522 - Logger precision formats in PatternLayout do not match documentation (Author: jvz [Member])

## Duplicate Groups
9 potential duplicate groups detected using semantic indexing:
- **Group 1:** #1287, #2689, #3415, #2314, #2461, #3504, #3667, #3100, #3401, #1741, #2347
- **Group 2:** #1976, #2033
- **Group 3:** #2352, #3423, #3599, #4024
- **Group 4:** #2383, #3780
- **Group 5:** #2769, #3875
- **Group 6:** #3086, #3509
- **Group 7:** #3128, #3185
- **Group 8:** #3172, #3173
- **Group 9:** #3623, #3895

## Ecosystem Connections & Downstream Impact

### JIRA Bridge Cross-Project Overlaps
The following issues discuss identical JIRA keys across repositories:
- Our Issue **#4037** matches **apache/kafka#21858** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **apache/kafka#21660** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **apache/flink#28203** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **apache/flink#27885** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **apache/flink#27837** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **opensearch-project/OpenSearch#22068** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **apache/solr#4445** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **quarkusio/quarkus#54742** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **quarkusio/quarkus#54670** via JIRA Key **CVE-2026**
- Our Issue **#4037** matches **apache/solr#4517** via JIRA Key **CVE-2026**
- Our Issue **#4064** matches **apache/flink#27676** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/flink#27479** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/ignite#12343** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/ignite#13038** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/ignite#13018** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/ignite#12685** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/ignite#12551** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **opensearch-project/OpenSearch#18760** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **opensearch-project/OpenSearch#18560** via JIRA Key **CVE-2025**
- Our Issue **#4064** matches **apache/flink#27745** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/flink#27676** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/flink#27479** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/ignite#12343** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/ignite#13038** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/ignite#13018** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/ignite#12685** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/ignite#12551** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **opensearch-project/OpenSearch#18760** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **opensearch-project/OpenSearch#18560** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/flink#27745** via JIRA Key **CVE-2025**
- Our Issue **#4126** matches **apache/kafka#21858** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **apache/kafka#21660** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **apache/flink#28203** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **apache/flink#27885** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **apache/flink#27837** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **opensearch-project/OpenSearch#22068** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **apache/solr#4445** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **quarkusio/quarkus#54742** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **quarkusio/quarkus#54670** via JIRA Key **CVE-2026**
- Our Issue **#4126** matches **apache/solr#4517** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/kafka#21858** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/kafka#21660** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/flink#28203** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/flink#27885** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/flink#27837** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **opensearch-project/OpenSearch#22068** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/solr#4445** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **quarkusio/quarkus#54742** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **quarkusio/quarkus#54670** via JIRA Key **CVE-2026**
- Our Issue **#4138** matches **apache/solr#4517** via JIRA Key **CVE-2026**

### Downstream References (Other projects linking to us)
- **apache/kafka#21290** references our Issue **#2475**

### Upstream Dependencies (We are linking to other projects)
- Our Issue **#1461** references **asciidoctor/asciidoctor-maven-plugin#578**
- Our Issue **#3335** references **apache/logging-log4j-samples#251**
- Our Issue **#3604** references **ossf/scorecard-webapp#554**
- Our Issue **#3780** references **apache/logging-parent#37**
- Our Issue **#4095** references **apache/logging-parent#456**
- Our Issue **#4114** references **apache/activemq#1799**
- Our Issue **#4114** references **apache/activemq#1801**
- Our Issue **#4114** references **apache/activemq#1804**
- Our Issue **#4114** references **apache/activemq#1806**
- Our Issue **#4114** references **apache/activemq#1808**
- Our Issue **#4114** references **apache/activemq#1810**
- Our Issue **#4114** references **apache/activemq#1817**
- Our Issue **#4114** references **apache/activemq#1834**
- Our Issue **#4114** references **apache/activemq#1836**
- Our Issue **#4114** references **apache/activemq#1838**
- Our Issue **#4114** references **apache/activemq#1844**
- Our Issue **#4114** references **apache/activemq#1848**
- Our Issue **#4114** references **apache/activemq#1860**
- Our Issue **#4114** references **apache/activemq#1868**
- Our Issue **#4114** references **apache/activemq#1884**
- Our Issue **#4114** references **apache/activemq#1886**
- Our Issue **#4114** references **apache/activemq#1892**
- Our Issue **#4114** references **apache/activemq#1894**
- Our Issue **#4114** references **apache/activemq#1902**
- Our Issue **#4114** references **apache/activemq#1915**
- Our Issue **#4114** references **apache/activemq#1919**
- Our Issue **#4114** references **apache/activemq#1923**
- Our Issue **#4114** references **apache/activemq#1925**
- Our Issue **#4114** references **apache/activemq#1928**
- Our Issue **#4114** references **apache/activemq#1930**
- Our Issue **#4114** references **apache/activemq#1934**
- Our Issue **#4114** references **apache/activemq#1942**
- Our Issue **#4114** references **apache/activemq#1952**
- Our Issue **#4114** references **apache/activemq#1954**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#975**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1008**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1013**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1015**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1018**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1019**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1020**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1021**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1023**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1024**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1025**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1027**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1028**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1029**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1030**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1031**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1032**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1033**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1036**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1037**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1038**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1039**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1040**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1041**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1042**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1043**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1045**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1046**
- Our Issue **#4126** references **CodeIntelligenceTesting/jazzer#1047**
- Our Issue **#4126** references **apache/activemq#1799**
- Our Issue **#4126** references **apache/activemq#1801**
- Our Issue **#4126** references **apache/activemq#1804**
- Our Issue **#4126** references **apache/activemq#1806**
- Our Issue **#4126** references **apache/activemq#1808**
- Our Issue **#4126** references **apache/activemq#1810**
- Our Issue **#4126** references **apache/activemq#1817**
- Our Issue **#4126** references **apache/activemq#1834**
- Our Issue **#4126** references **apache/activemq#1836**
- Our Issue **#4126** references **apache/activemq#1838**
- Our Issue **#4126** references **apache/activemq#1844**
- Our Issue **#4126** references **apache/activemq#1848**
- Our Issue **#4126** references **apache/activemq#1860**
- Our Issue **#4126** references **apache/activemq#1868**
- Our Issue **#4126** references **apache/activemq#1884**
- Our Issue **#4126** references **apache/activemq#1886**
- Our Issue **#4126** references **apache/activemq#1892**
- Our Issue **#4126** references **apache/activemq#1894**
- Our Issue **#4126** references **apache/activemq#1902**
- Our Issue **#4126** references **apache/activemq#1915**
- Our Issue **#4126** references **apache/activemq#1919**
- Our Issue **#4126** references **apache/activemq#1923**
- Our Issue **#4126** references **apache/activemq#1925**
- Our Issue **#4126** references **apache/activemq#1928**
- Our Issue **#4126** references **apache/activemq#1930**
- Our Issue **#4126** references **apache/activemq#1934**
- Our Issue **#4126** references **apache/activemq#1942**
- Our Issue **#4126** references **apache/activemq#1952**
- Our Issue **#4126** references **apache/activemq#1954**
- Our Issue **#4126** references **junit-team/junit-framework#5245**
- Our Issue **#4137** references **actions/stale#1311**
- Our Issue **#4137** references **actions/stale#1335**
- Our Issue **#4138** references **junit-team/junit-framework#4574**
- Our Issue **#4138** references **junit-team/junit-framework#5006**
- Our Issue **#4138** references **junit-team/junit-framework#5041**
- Our Issue **#4138** references **junit-team/junit-framework#5066**
- Our Issue **#4138** references **junit-team/junit-framework#5088**
- Our Issue **#4138** references **junit-team/junit-framework#5092**
- Our Issue **#4138** references **junit-team/junit-framework#5133**
- Our Issue **#4138** references **junit-team/junit-framework#5145**
- Our Issue **#4138** references **junit-team/junit-framework#5271**
- Our Issue **#4138** references **junit-team/junit-framework#5290**
- Our Issue **#4138** references **junit-team/junit-framework#5316**
- Our Issue **#4138** references **junit-team/junit-framework#5427**
- Our Issue **#4138** references **junit-team/junit-framework#5561**
- Our Issue **#4138** references **junit-team/junit-framework#5602**
- Our Issue **#4138** references **junit-team/junit-framework#5633**
- Our Issue **#4138** references **junit-team/junit-framework#5644**
- Our Issue **#4138** references **mockito/mockito#3285**
- Our Issue **#4138** references **mockito/mockito#3503**
- Our Issue **#4138** references **mockito/mockito#3652**
- Our Issue **#4138** references **mockito/mockito#3733**
- Our Issue **#4138** references **mockito/mockito#3734**
- Our Issue **#4138** references **mockito/mockito#3735**
- Our Issue **#4138** references **mockito/mockito#3738**
- Our Issue **#4138** references **mockito/mockito#3743**
- Our Issue **#4138** references **mockito/mockito#3744**
- Our Issue **#4138** references **mockito/mockito#3752**
- Our Issue **#4138** references **mockito/mockito#3753**
- Our Issue **#4138** references **mockito/mockito#3755**
- Our Issue **#4138** references **mockito/mockito#3756**
- Our Issue **#4138** references **mockito/mockito#3758**
- Our Issue **#4138** references **mockito/mockito#3759**
- Our Issue **#4138** references **mockito/mockito#3760**
- Our Issue **#4138** references **mockito/mockito#3762**
- Our Issue **#4138** references **mockito/mockito#3765**
- Our Issue **#4138** references **mockito/mockito#3767**
- Our Issue **#4138** references **mockito/mockito#3768**
- Our Issue **#4138** references **mockito/mockito#3771**
- Our Issue **#4138** references **mockito/mockito#3773**
- Our Issue **#4138** references **mockito/mockito#3774**
- Our Issue **#4138** references **mockito/mockito#3778**
- Our Issue **#4138** references **mockito/mockito#3780**
- Our Issue **#4138** references **mockito/mockito#3785**
- Our Issue **#4138** references **mockito/mockito#3790**
- Our Issue **#4138** references **mockito/mockito#3792**

## Stale PRs
18 pull requests have had no activity for over 30 days:
1. #3248 - Migrate log4j-iostreams to use JUnit 5 APIs and compatible helper classes (Author: jcoglan [Member], 545 days inactive)
2. #3333 - Fixes in dev docs (Author: grobmeier [Member], 526 days inactive)
3. #3468 - Modernise `RollingRandomAppenderDirectWriteAndSwitchDirectoryTest` (Author: ppkarwasz [Member], 478 days inactive)
4. #3568 - Exclude certain standard library classes from docgen type exclusion (#3423) (Author: jbb01, 386 days inactive)
5. #3583 - [main] Correct example property syntax for PatternMatch under ScriptP… (Author: ppkarwasz [Member], 422 days inactive)
6. #3588 - Update pattern logger precision  example and description (Author: cretzbach, 431 days inactive)
7. #3657 - Add Max Base as collaborator (Author: ppkarwasz [Member], 389 days inactive)
8. #3658 - fix toString() method in configuration implementations (apache#3599) (Author: anindita-sarkarArray, 358 days inactive)
9. #3719 - Refactor StatusLogger to use precompiled Pattern constants (Author: kamilkrzywanski, 366 days inactive)
10. #3744 - Documentation Update for Logger Layout Pattern Precision Example (Author: zengshengliu, 349 days inactive)
11. #3812 - Respect `log4j1.compatibility` for programmatic configuration (Author: jhl221123, 335 days inactive)
12. #3869 - Synchronize Log4jLogEvent-related changes from 2.x (Author: jvz [Member], 230 days inactive)
13. #3877 - Simplify name and alias annotation processing (Author: jvz [Member], 230 days inactive)
14. #3902 - Unify TLS configuration (Author: jhl221123, 264 days inactive)
15. #3913 - feat: flatten non-BOM artifacts (Author: ppkarwasz [Member], 264 days inactive)
16. #4011 - feature:support random delayed compression (Author: OpenZYK, 42 days inactive)
17. #4094 - Add SLSA Source Level 4 provenance workflow (Author: ppkarwasz [Member], 56 days inactive)
18. #4104 - Add log4j-iceberg appender module for writing logs to Apache Iceberg tables (Author: gsoundar, 35 days inactive)
