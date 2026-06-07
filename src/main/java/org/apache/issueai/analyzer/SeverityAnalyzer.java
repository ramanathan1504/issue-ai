package org.apache.issueai.analyzer;

import org.apache.issueai.model.Issue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

public class SeverityAnalyzer {

    private static Pattern wordPattern(String s) {
        return Pattern.compile("\\b" + Pattern.quote(s) + "\\b");
    }

    private static final Pattern SUPPORT = wordPattern("support");
    private static final Pattern ENHANCEMENT = wordPattern("enhancement");
    private static final Pattern DEPRECATE = wordPattern("deprecate");
    private static final Pattern DOCUMENT = wordPattern("document");
    private static final Pattern FEATURE = wordPattern("feature");
    private static final Pattern MIGRATION = wordPattern("migration");
    private static final Pattern MEMORY_LEAK = wordPattern("memory leak");
    private static final Pattern DEADLOCK = wordPattern("deadlock");
    private static final Pattern HANG = wordPattern("hang");
    private static final Pattern CONTENTION =

            wordPattern("contention");
    private static final Pattern SUPPORT_FOR =
            Pattern.compile("\\bsupport\\s+for\\b");
    private static final Pattern NEW_FEATURE =
            Pattern.compile("\\bnew\\s+feature\\b");    private static final Pattern MISSING_ANSI = wordPattern("missing ansi");
    private static final Pattern STYLE = wordPattern("style");
    private static final Pattern DOCUMENTATION = wordPattern("documentation");
    private static final Pattern MEMORY_USAGE =

            Pattern.compile("\\bmemory\\s+usage\\b");

    private static final Pattern HEAP_LEAK =

            Pattern.compile("\\bheap\\s+leak\\b");

    private static final Pattern FAILS_TO_START =
            Pattern.compile("\\bfails?\\s+to\\s+start\\b");
    private static final Pattern MEMORY =
            wordPattern("memory");
    private static final Pattern STARTUP =
            wordPattern("startup");
    private static final Pattern CANT_START =
            Pattern.compile("\\bcan'?t\\s+start\\b");

    private static Pattern labelPattern(String s) {

        return Pattern.compile("\\b" + Pattern.quote(s) + "\\b", Pattern.CASE_INSENSITIVE);
    }

    private static final Pattern BUG_LABEL = labelPattern("bug");
    private static final Pattern PERFORMANCE_LABEL = labelPattern("performance");
    private static final Pattern SECURITY_LABEL = labelPattern("security");
    private static final Pattern DOCUMENTATION_LABEL = labelPattern("documentation");
    private static final Pattern WAITING_FOR_MAINTAINER =

            labelPattern("waiting-for-maintainer");
    private static boolean labelMatches(Issue issue, Pattern p) {
        return issue != null && issue.labels() != null &&
                issue.labels().stream()
                        .anyMatch(label -> p.matcher(label.name()).find());
    }

    public IssueAnalysis analyze(Issue issue) {

        String text =
                (issue.title() + " " +
                        (issue.body() == null ? "" : issue.body()))
                        .toLowerCase();

        int score = 0;
        StringBuilder reason = new StringBuilder();
        long daysOpen =

                ChronoUnit.DAYS.between(

                        Instant.parse(issue.created_at()),

                        Instant.now());

        if (daysOpen > 365) {

            score += 10;

        }

        if (daysOpen > 730) {

            score += 20;

        }
        long daysSinceUpdate =
                ChronoUnit.DAYS.between(
                        Instant.parse(issue.updated_at()),
                        Instant.now());

        if (daysSinceUpdate < 30) {
            score += 15;
        }
        if (SUPPORT.matcher(text).find()) {
            score -= 40;
        }

        if (ENHANCEMENT.matcher(text).find()) {
            score -= 40;
        }

        if (DOCUMENT.matcher(text).find()) {
            score -= 50;
        }

        if (FEATURE.matcher(text).find()) {
            score -= 40;
        }
        if (STARTUP.matcher(text).find()) {
            score += 60;
            reason.append("startup ");
        }
        if (MIGRATION.matcher(text).find()) {
            score -= 30;
        }
        if (MEMORY.matcher(text).find()) {
            score += 20;
            reason.append("memory ");
        }
        if (FAILS_TO_START.matcher(text).find()) {

            score += 80;

            reason.append("startup-failure ");

        }
        if (labelMatches(issue, WAITING_FOR_MAINTAINER)) {

            score += 10;

            reason.append("waiting-for-maintainer ");

        }
        if (CANT_START.matcher(text).find()) {

            score += 80;

            reason.append("startup-failure ");

        }
        // Keyword scoring
        if (MEMORY_LEAK.matcher(text).find()) {
            score += 100;
            reason.append("memory leak ");
        }
        if (MEMORY_USAGE.matcher(text).find()) {

            score += 80;

            reason.append("memory-usage ");

        }

        if (HEAP_LEAK.matcher(text).find()) {

            score += 100;

            reason.append("heap-leak ");

        }

        if (DEADLOCK.matcher(text).find()) {
            score += 100;
            reason.append("deadlock ");
        }

        if (HANG.matcher(text).find()) {

            System.out.println(

                    "HANG MATCH: #" +

                            issue.number() +

                            " -> " +

                            issue.title());

            score += 80;

            reason.append("hang ");

        }
        if (CONTENTION.matcher(text).find()) {
            score += 60;
            reason.append("contention ");
        }
        // Label scoring (use pattern matching to allow label variations)
        if (labelMatches(issue, BUG_LABEL)) {
            score += 20;
            reason.append("bug-label ");
        }

        if (labelMatches(issue, PERFORMANCE_LABEL)) {
            score += 30;
            reason.append("performance-label ");
        }

        if (labelMatches(issue, SECURITY_LABEL)) {
            score += 100;
            reason.append("security-label ");
        }

        if (labelMatches(issue, DOCUMENTATION_LABEL)) {
            score -= 50;
            reason.append("documentation-label ");
        }
        if (SUPPORT_FOR.matcher(text).find()) {
            score -= 60;
        }

        if (NEW_FEATURE.matcher(text).find()) {
            score -= 60;
        }

        if (MISSING_ANSI.matcher(text).find()) {
            score -= 50;
        }

        if (STYLE.matcher(text).find()) {
            score -= 30;
        }

        if (DOCUMENTATION.matcher(text).find()) {
            score -= 50;
        }

        if (DEPRECATE.matcher(text).find()) {
            score -= 40;
        }

        // Comment scoring
        score += Math.min(issue.comments(), 20);

        Severity severity =
                score >= 120 ? Severity.CRITICAL :
                        score >= 80 ? Severity.HIGH :
                        score >= 40 ? Severity.MEDIUM :
                        Severity.LOW;
        if (score >= 80) {

            System.out.printf(

                    "#%d score=%d reason=%s%n",

                    issue.number(),

                    score,

                    reason);

        }
        return new IssueAnalysis(
                issue,
                severity,
                score,
                reason.toString()
        );
    }


}



