
package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import com.adgroup.aicodereview.github.GitHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PullRequestFindingValidator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PullRequestFindingValidator.class);

    private static final String DELETED_LINE_NOTE =
            "This finding refers to a line deleted from the pull request's base snapshot.";

    private PullRequestFindingValidator() {
    }

    static ValidationResult validate(
            List<ReviewFinding> findings,
            GitHubService.PullRequestSnapshot snapshot) {

        if (findings == null) {
            return new ValidationResult(List.of(), 0);
        }

        List<ReviewFinding> verified = new ArrayList<>();
        int omitted = 0;

        for (ReviewFinding finding : findings) {
            if (finding == null) {
                continue;
            }

            String path = normalizeFindingPath(finding.getFile());
            Integer line = finding.getLine();

            // 1. Validate finding identity.
            if (path == null || line == null || line <= 0
                    || finding.getProblem() == null
                    || finding.getProblem().isBlank()) {

                LOGGER.warn(
                        "Omitting finding: invalid identity. file={}, line={}, problem={}",
                        finding.getFile(), finding.getLine(), finding.getProblem());

                omitted++;
                continue;
            }

            // 2. Match the finding to a changed PR file.
            GitHubService.ChangedFileSnapshot changedFile =
                    snapshot.changedFiles().stream()
                            .filter(file ->
                                    path.equals(file.changes().newPath())
                                            || path.equals(file.changes().oldPath()))
                            .findFirst()
                            .orElse(null);

            if (changedFile == null) {
                LOGGER.warn(
                        "Omitting finding: file does not match any changed PR file. file={}",
                        finding.getFile());

                omitted++;
                continue;
            }

            // 3. Reject binary files.
            if (changedFile.changes().binary()) {
                LOGGER.warn(
                        "Omitting finding: changed file is binary. file={}",
                        finding.getFile());

                omitted++;
                continue;
            }

            // 4. Determine whether the reported line was added or deleted.
            boolean addedLine =
                    path.equals(changedFile.changes().newPath())
                            && changedFile.changes().hasAddedLine(line);

            boolean deletedLine =
                    path.equals(changedFile.changes().oldPath())
                            && changedFile.changes().hasDeletedLine(line);

            if (addedLine && deletedLine) {
                LOGGER.warn(
                        "Omitting finding: line is both added and deleted. file={}, line={}",
                        finding.getFile(), line);

                omitted++;
                continue;
            }

            // 5. Validate findings on added lines.
            if (addedLine) {
                if (hasValidSourceLine(
                        changedFile.newSupported(),
                        changedFile.newContent(),
                        line)) {

                    verified.add(finding);

                } else {
                    String content = changedFile.newContent();

                    LOGGER.warn(
                            "Omitting finding: added-line source unavailable or "
                                    + "line out of range. file={}, line={}, "
                                    + "sourceSupported={}, sourceLineCount={}",
                            finding.getFile(),
                            line,
                            changedFile.newSupported(),
                            content == null ? -1 : lineCount(content));

                    omitted++;
                }

                continue;
            }

            // 6. Validate findings on deleted lines.
            if (deletedLine) {
                if (hasValidSourceLine(
                        changedFile.oldSupported(),
                        changedFile.oldContent(),
                        line)) {

                    appendDeletedLineNote(finding);
                    verified.add(finding);

                } else {
                    String content = changedFile.oldContent();

                    LOGGER.warn(
                            "Omitting finding: deleted-line source unavailable or "
                                    + "line out of range. file={}, line={}, "
                                    + "sourceSupported={}, sourceLineCount={}",
                            finding.getFile(),
                            line,
                            changedFile.oldSupported(),
                            content == null ? -1 : lineCount(content));

                    omitted++;
                }

                continue;
            }

            // 7. Neither the added nor deleted line matches.
            LOGGER.warn(
                    "Omitting finding: line is not recognized as changed. file={}, line={}",
                    finding.getFile(), line);

            omitted++;
        }

        return new ValidationResult(List.copyOf(verified), omitted);
    }

    private static boolean hasValidSourceLine(
            boolean supported,
            String content,
            int line) {

        return supported
                && content != null
                && line <= lineCount(content);
    }

    private static void appendDeletedLineNote(ReviewFinding finding) {
        String explanation = finding.getExplanation();

        if (explanation == null || explanation.isBlank()) {
            finding.setExplanation(DELETED_LINE_NOTE);
        } else if (!explanation.contains(DELETED_LINE_NOTE)) {
            finding.setExplanation(
                    explanation.trim() + " " + DELETED_LINE_NOTE);
        }
    }

    private static int lineCount(String content) {
        if (content.isEmpty()) {
            return 0;
        }

        int newlines = 0;

        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '\n') {
                newlines++;
            }
        }

        return content.charAt(content.length() - 1) == '\n'
                ? newlines
                : newlines + 1;
    }

    private static String normalizeFindingPath(String file) {
        if (file == null || file.isBlank() || file.indexOf(':') >= 0) {
            return null;
        }

        try {
            Path path = Path.of(file.replace('\\', '/'));

            if (path.isAbsolute()
                    || !path.normalize().equals(path)
                    || path.normalize().startsWith("..")) {
                return null;
            }

            return path.toString().replace('\\', '/');

        } catch (InvalidPathException e) {
            return null;
        }
    }

    record ValidationResult(List<ReviewFinding> findings, int omittedCount) {
    }
}
