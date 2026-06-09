package org.apache.issueai.cli;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.issueai.analyzer.Severity;
import org.apache.issueai.analyzer.SeverityAnalyzer;
import org.apache.issueai.model.Issue;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "prs",
        description = "Analyze cached open pull requests for stale status, reviews, and critical fixes"
)
public class PrsCommand implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository to analyze (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;
    private static final Pattern ISSUE_REF_PATTERN = Pattern.compile("#(\\d+)");

    @Override
    public Integer call() throws Exception {
        List<Issue> prs = SqliteStorage.loadPullRequests(repository);
        List<Issue> issues = SqliteStorage.loadIssues(repository);

        if (prs.isEmpty()) {
            System.err.println("No local pull request data found. Please run 'sync' first.");
            return 1;
        }

        // 1. Identify all Critical Issues locally to check for critical fixes
        SeverityAnalyzer severityAnalyzer = new SeverityAnalyzer();
        Set<Integer> criticalIssueNumbers = new HashSet<>();

        if (issues != null) {
            issues.stream()
                    .map(severityAnalyzer::analyze)
                    .filter(analysis -> analysis.severity() == Severity.CRITICAL)
                    .forEach(analysis -> criticalIssueNumbers.add((int) analysis.issue().number()));
        }

        System.out.println("Pull Request Intelligence Report");
        System.out.println("================================\n");

        List<Issue> stalePrs = new ArrayList<>();
        List<Issue> reviewsNeeded = new ArrayList<>();
        List<String> criticalFixes = new ArrayList<>();

        Instant now = Instant.now();

        for (Issue pr : prs) {
            Instant createdAt = Instant.parse(pr.created_at());
            Instant updatedAt = Instant.parse(pr.updated_at());

            long daysOpen = ChronoUnit.DAYS.between(createdAt, now);
            long daysSinceUpdate = ChronoUnit.DAYS.between(updatedAt, now);

            // A. Check for Stale status (> 30 days of inactivity)
            if (daysSinceUpdate > 30) {
                stalePrs.add(pr);
            }

            // B. Check if Review is Needed
            // (Using comments == 0 as a compile-safe proxy since reviews are not in the model)
            if (pr.comments() == 0) {
                reviewsNeeded.add(pr);
            }

            // C. Check for Critical Fix references
            Set<Integer> linkedIssueNumbers = extractLinkedIssues(pr.title() + " " + pr.body());
            for (Integer num : linkedIssueNumbers) {
                if (criticalIssueNumbers.contains(num)) {
                    criticalFixes.add(String.format(
                            "PR #%d  Fixes: Issue #%d  Severity: Critical  Waiting: %d days",
                            pr.number(),
                            num,
                            daysOpen));
                }
            }
        }

        // Print Stale PRs
        System.out.println("STALE PULL REQUESTS (No activity > 30 days)");
        System.out.println("------------------------------------------");
        if (stalePrs.isEmpty()) {
            System.out.println("(none)");
        } else {
            for (Issue pr : stalePrs) {
                long daysSinceUpdate = ChronoUnit.DAYS.between(Instant.parse(pr.updated_at()), now);
                System.out.printf(
                        "#%d  Title: %s  Last Activity: %d days ago%n",
                        pr.number(),
                        pr.title(),
                        daysSinceUpdate);
            }
        }
        System.out.println();

        // Print Reviews Needed
        System.out.println("REVIEW NEEDED (No comments/reviews yet)");
        System.out.println("---------------------------------------");
        if (reviewsNeeded.isEmpty()) {
            System.out.println("(none)");
        } else {
            for (Issue pr : reviewsNeeded) {
                long daysOpen = ChronoUnit.DAYS.between(Instant.parse(pr.created_at()), now);
                System.out.printf(
                        "#%d  Title: %s  Waiting: %d days%n",
                        pr.number(),
                        pr.title(),
                        daysOpen);
            }
        }
        System.out.println();

        // Print Critical Fixes
        System.out.println("CRITICAL FIXES (PRs linked to Critical Issues)");
        System.out.println("----------------------------------------------");
        if (criticalFixes.isEmpty()) {
            System.out.println("(none)");
        } else {
            for (String line : criticalFixes) {
                System.out.println(line);
            }
        }
        System.out.println();

        return 0;
    }

    private Set<Integer> extractLinkedIssues(String text) {
        Set<Integer> numbers = new HashSet<>();
        if (text == null) {
            return numbers;
        }
        Matcher matcher = ISSUE_REF_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore parsing errors
            }
        }
        return numbers;
    }
}