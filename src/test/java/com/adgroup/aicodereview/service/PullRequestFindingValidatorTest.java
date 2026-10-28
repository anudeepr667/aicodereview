package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import com.adgroup.aicodereview.github.GitHubService;
import com.adgroup.aicodereview.github.PullRequestDiff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullRequestFindingValidatorTest {

    @Test
    void acceptsOnlyChangedLinesWithinTheirSnapshotFileBounds() {
        GitHubService.PullRequestSnapshot snapshot = snapshot(
                changes("src/App.java", "src/App.java",
                        List.of(new PullRequestDiff.LineRange(3, 3),
                                new PullRequestDiff.LineRange(9, 9)),
                        List.of()),
                "one\ntwo\nthree\nfour\nfive\n");
        List<ReviewFinding> findings = List.of(
                finding("src/App.java", 3),
                finding("src/App.java", 1),
                finding("src/App.java", 9),
                finding("../outside.java", 3));

        PullRequestFindingValidator.ValidationResult result =
                PullRequestFindingValidator.validate(findings, snapshot);

        assertEquals(List.of(findings.get(0)), result.findings());
        assertEquals(3, result.omittedCount());
    }

    @Test
    void acceptsDeletedOldSideLinesAndClearlyMarksTheirCoordinates() {
        GitHubService.PullRequestSnapshot snapshot = snapshot(
                changes("src/App.java", "src/App.java",
                        List.of(new PullRequestDiff.LineRange(2, 2)),
                        List.of(new PullRequestDiff.LineRange(5, 5))),
                "one\ntwo\n",
                "one\ntwo\nthree\nfour\nfive\n",
                true,
                true);
        ReviewFinding deleted = finding("src/App.java", 5);

        PullRequestFindingValidator.ValidationResult result =
                PullRequestFindingValidator.validate(List.of(deleted), snapshot);

        assertEquals(List.of(deleted), result.findings());
        assertEquals(0, result.omittedCount());
        assertTrue(deleted.getExplanation().contains("deleted from the pull request's base snapshot"));
    }

    @Test
    void omitsAmbiguousReplacementLineCoordinatesInsteadOfTreatingThemAsCurrent() {
        GitHubService.PullRequestSnapshot snapshot = snapshot(
                changes("src/App.java", "src/App.java",
                        List.of(new PullRequestDiff.LineRange(5, 5)),
                        List.of(new PullRequestDiff.LineRange(5, 5))),
                "one\ntwo\nthree\nfour\nnew line\n",
                "one\ntwo\nthree\nfour\nold line\n",
                true,
                true);
        ReviewFinding ambiguous = finding("src/App.java", 5);

        PullRequestFindingValidator.ValidationResult result =
                PullRequestFindingValidator.validate(List.of(ambiguous), snapshot);

        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.omittedCount());
        assertNull(ambiguous.getExplanation());
    }

    @Test
    void validatesRenamedFilesAgainstTheCorrectOldAndNewPaths() {
        GitHubService.PullRequestSnapshot snapshot = snapshot(
                changes("src/Old.java", "src/New.java",
                        List.of(new PullRequestDiff.LineRange(2, 2)),
                        List.of(new PullRequestDiff.LineRange(4, 4))),
                "new one\nnew two\n",
                "old one\nold two\nold three\nold four\n",
                true,
                true);
        ReviewFinding currentPath = finding("src/New.java", 2);
        ReviewFinding previousPath = finding("src/Old.java", 4);

        PullRequestFindingValidator.ValidationResult result =
                PullRequestFindingValidator.validate(
                        List.of(currentPath, previousPath), snapshot);

        assertEquals(List.of(currentPath, previousPath), result.findings());
        assertEquals(0, result.omittedCount());
        assertTrue(previousPath.getExplanation().contains("deleted from the pull request's base snapshot"));
    }

    @Test
    void omitsUnsupportedFilesAndFindingsWithoutCompleteCoordinates() {
        GitHubService.PullRequestSnapshot snapshot = snapshot(
                changes("README.md", "README.md",
                        List.of(new PullRequestDiff.LineRange(1, 1)),
                        List.of()),
                null,
                null,
                false,
                false);
        ReviewFinding unsupported = finding("README.md", 1);
        ReviewFinding noLine = finding("README.md", null);

        PullRequestFindingValidator.ValidationResult result =
                PullRequestFindingValidator.validate(List.of(unsupported, noLine), snapshot);

        assertTrue(result.findings().isEmpty());
        assertEquals(2, result.omittedCount());
    }

    @Test
    void omitsFindingsFromBinaryFiles() {
        PullRequestDiff.FileChanges binaryChanges = new PullRequestDiff.FileChanges(
                "assets/logo.png",
                "assets/logo.png",
                List.of(new PullRequestDiff.LineRange(1, 1)),
                List.of(),
                true);
        GitHubService.PullRequestSnapshot snapshot =
                snapshot(binaryChanges, null, null, false, false);

        PullRequestFindingValidator.ValidationResult result =
                PullRequestFindingValidator.validate(
                        List.of(finding("assets/logo.png", 1)), snapshot);

        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.omittedCount());
    }

    private static GitHubService.PullRequestSnapshot snapshot(
            PullRequestDiff.FileChanges changes,
            String newContent) {
        return snapshot(changes, newContent, null, false, true);
    }

    private static GitHubService.PullRequestSnapshot snapshot(
            PullRequestDiff.FileChanges changes,
            String newContent,
            String oldContent,
            boolean oldSupported,
            boolean newSupported) {
        return new GitHubService.PullRequestSnapshot(
                "",
                "base",
                "head",
                "merge-base",
                List.of(new GitHubService.ChangedFileSnapshot(
                        changes, oldContent, newContent, oldSupported, newSupported)));
    }

    private static PullRequestDiff.FileChanges changes(
            String oldPath,
            String newPath,
            List<PullRequestDiff.LineRange> added,
            List<PullRequestDiff.LineRange> deleted) {
        return new PullRequestDiff.FileChanges(oldPath, newPath, added, deleted, false);
    }

    private static ReviewFinding finding(String file, Integer line) {
        ReviewFinding finding = new ReviewFinding();
        finding.setFile(file);
        finding.setLine(line);
        finding.setProblem("A concrete issue");
        return finding;
    }
}
