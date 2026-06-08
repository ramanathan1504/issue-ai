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
1. #4009 - Deadlock when use TimeoutBlockingWaitStrategy
2. #3569 - Memory Usage Increase in Log4j2 2.24.3 with Async Logging Due to RingBufferLogEvent
3. #3282 - When logging exceptions, the exception stack traces include dynamically generated class names, which leads to a memory leak.
4. #2111 - Startup on Mac is extremely slow consistently.
5. #1666 - kafka appender: When kafka server is down, application can't start

## Hidden Critical
1. #4088 - Log messages appear to be trimmed when using PatternLayout %m%n
2. #3600 - [main] Add transitive `compileOnlyApi` dependencies
3. #2492 - Rollover not works appropriotely with CronTriggeringPolicy and SizeBasedTriggeringPolicy
4. #2466 - Log4J in desktop application
5. #2297 - SizeBasedTriggeringPolicy counter not resetting after TimeBasedTriggeringPolicy rollover
6. #2020 - RandomAccessFileAppender does not write header to file
7. #1952 - Deprecate modules/features for removal in `3.x`

## Duplicate Groups
9 clusters detected using semantic indexing.

## Stale PRs
26 pull requests have had no activity for over 30 days.
