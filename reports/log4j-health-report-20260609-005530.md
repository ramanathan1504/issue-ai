# Apache Log4j Weekly Health Report

Generated: 2026-06-09

## Summary

- Open Issues: 159
- Open PRs: 42
- Critical Issues Count: 5
- Hidden Critical Count: 7
- Duplicate Clusters Count: 9
- Stale PRs Count: 26

## Critical Issues
1. #4009 - Deadlock when use TimeoutBlockingWaitStrategy (Author: cs-xiao)
2. #3569 - Memory Usage Increase in Log4j2 2.24.3 with Async Logging Due to RingBufferLogEvent (Author: shaonan666)
3. #3282 - When logging exceptions, the exception stack traces include dynamically generated class names, which leads to a memory leak. (Author: Tonys-L)
4. #2111 - Startup on Mac is extremely slow consistently. (Author: brr53)
5. #1666 - kafka appender: When kafka server is down, application can't start (Author: ldcsaa)

## Hidden Critical
1. #4088 - Log messages appear to be trimmed when using PatternLayout %m%n (Author: veenaBOL)
2. #3600 - [main] Add transitive `compileOnlyApi` dependencies (Author: ppkarwasz [Member])
3. #2492 - Rollover not works appropriotely with CronTriggeringPolicy and SizeBasedTriggeringPolicy (Author: minkyu97)
4. #2466 - Log4J in desktop application (Author: tmmlsBE)
5. #2297 - SizeBasedTriggeringPolicy counter not resetting after TimeBasedTriggeringPolicy rollover (Author: ppkarwasz [Member])
6. #2020 - RandomAccessFileAppender does not write header to file (Author: Xfel)
7. #1952 - Deprecate modules/features for removal in `3.x` (Author: ppkarwasz [Member])

## Duplicate Groups
9 potential duplicate groups detected using semantic indexing:
- **Group 1:** #4024, #2352, #3599, #3423
- **Group 2:** #3895, #3623
- **Group 3:** #3875, #2769
- **Group 4:** #3780, #2383
- **Group 5:** #3667, #3504, #3415, #3100, #2461, #1741, #3401, #1287, #2347, #2689, #2314
- **Group 6:** #3509, #3086
- **Group 7:** #3185, #3128
- **Group 8:** #3173, #3172
- **Group 9:** #2033, #1976

## Stale PRs
26 pull requests have had no activity for over 30 days:
1. #4114 - Bump org.apache.activemq:activemq-broker from 6.1.7 to 6.2.5 in /log4j-parent (Author: dependabot[bot], 34 days inactive)
2. #4104 - Add log4j-iceberg appender module for writing logs to Apache Iceberg tables (Author: gsoundar, 33 days inactive)
3. #4101 - Bump org.apache.kafka:kafka-clients from 3.9.1 to 3.9.2 in /log4j-parent (Author: dependabot[bot], 49 days inactive)
4. #4097 - Bump org.bouncycastle:bcpkix-jdk18on from 1.83 to 1.84 in /log4j-core-test (Author: dependabot[bot], 52 days inactive)
5. #4094 - Add SLSA Source Level 4 provenance workflow (Author: ppkarwasz [Member], 54 days inactive)
6. #4093 - Bump org.apache.kafka:kafka-clients from 3.9.1 to 3.9.2 (Author: dependabot[bot], 55 days inactive)
7. #4084 - Bump org.codehaus.plexus:plexus-utils from 3.6.0 to 4.0.3 in /log4j-parent (Author: dependabot[bot], 37 days inactive)
8. #4037 - Bump org.assertj:assertj-core from 3.27.3 to 3.27.7 in /log4j-parent (Author: dependabot[bot], 37 days inactive)
9. #4011 - feature:support random delayed compression (Author: OpenZYK, 40 days inactive)
10. #3986 - Bump ch.qos.logback:logback-core from 1.3.15 to 1.3.16 (Author: dependabot[bot], 194 days inactive)
11. #3977 - Bump ch.qos.logback:logback-core from 1.3.15 to 1.3.16 in /log4j-parent (Author: dependabot[bot], 219 days inactive)
12. #3913 - feat: flatten non-BOM artifacts (Author: ppkarwasz [Member], 263 days inactive)
13. #3902 - Unify TLS configuration (Author: jhl221123, 263 days inactive)
14. #3877 - Simplify name and alias annotation processing (Author: jvz [Member], 229 days inactive)
15. #3869 - Synchronize Log4jLogEvent-related changes from 2.x (Author: jvz [Member], 229 days inactive)
16. #3812 - Respect `log4j1.compatibility` for programmatic configuration (Author: jhl221123, 333 days inactive)
17. #3744 - Documentation Update for Logger Layout Pattern Precision Example (Author: zengshengliu, 348 days inactive)
18. #3719 - Refactor StatusLogger to use precompiled Pattern constants (Author: kamilkrzywanski, 364 days inactive)
19. #3658 - fix toString() method in configuration implementations (apache#3599) (Author: anindita-sarkarArray, 357 days inactive)
20. #3657 - Add Max Base as collaborator (Author: ppkarwasz [Member], 388 days inactive)
21. #3588 - Update pattern logger precision  example and description (Author: cretzbach, 430 days inactive)
22. #3583 - [main] Correct example property syntax for PatternMatch under ScriptP… (Author: ppkarwasz [Member], 420 days inactive)
23. #3568 - Exclude certain standard library classes from docgen type exclusion (#3423) (Author: jbb01, 384 days inactive)
24. #3468 - Modernise `RollingRandomAppenderDirectWriteAndSwitchDirectoryTest` (Author: ppkarwasz [Member], 477 days inactive)
25. #3333 - Fixes in dev docs (Author: grobmeier [Member], 525 days inactive)
26. #3248 - Migrate log4j-iostreams to use JUnit 5 APIs and compatible helper classes (Author: jcoglan [Member], 543 days inactive)
