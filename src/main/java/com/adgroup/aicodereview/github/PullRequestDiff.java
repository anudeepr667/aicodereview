package com.adgroup.aicodereview.github;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PullRequestDiff(List<FileChanges> files) {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    public static PullRequestDiff parse(String diff) {
        if (diff == null) {
            throw new IllegalStateException("GitHub returned no pull request diff.");
        }
        List<FileChanges> files = new ArrayList<>();
        MutableFile current = null;
        int oldLine = 0;
        int newLine = 0;
        boolean inHunk = false;
        for (String line : diff.split("\\n", -1)) {
            if (line.startsWith("diff --git ")) {
                if (current != null) {
                    files.add(current.build());
                }
                String[] paths = parseGitHeader(line.substring("diff --git ".length()));
                current = new MutableFile(paths[0], paths[1]);
                inHunk = false;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (line.startsWith("Binary files ") || line.equals("GIT binary patch")) {
                current.binary = true;
                continue;
            }
            if (!inHunk && line.startsWith("--- ")) {
                current.oldPath = parseFileHeader(line.substring(4), "a/");
                continue;
            }
            if (!inHunk && line.startsWith("+++ ")) {
                current.newPath = parseFileHeader(line.substring(4), "b/");
                continue;
            }

            Matcher hunk = HUNK_HEADER.matcher(line);
            if (hunk.matches()) {
                oldLine = Integer.parseInt(hunk.group(1));
                newLine = Integer.parseInt(hunk.group(3));
                inHunk = true;
                continue;
            }
            if (line.startsWith("\\") || line.isEmpty()) {
                continue;
            }
            if (line.startsWith("+")) {
                current.addedLines.add(newLine++);
            } else if (line.startsWith("-")) {
                current.deletedLines.add(oldLine++);
            } else if (line.startsWith(" ")) {
                oldLine++;
                newLine++;
            }
        }
        if (current != null) {
            files.add(current.build());
        }
        return new PullRequestDiff(List.copyOf(files));
    }

    private static String[] parseGitHeader(String header) {
        if (header.startsWith("\"")) {
            Token oldToken = readQuotedToken(header, 0);
            int next = skipSpaces(header, oldToken.end());
            Token newToken = readQuotedToken(header, next);
            return new String[] {
                    stripPrefix(oldToken.value(), "a/"),
                    stripPrefix(newToken.value(), "b/")
            };
        }
        int separator = header.lastIndexOf(" b/");
        if (separator < 0 || !header.startsWith("a/")) {
            throw new IllegalStateException("GitHub returned an invalid pull request diff path.");
        }
        return new String[] {
                header.substring(2, separator),
                header.substring(separator + 3)
        };
    }

    private static String parseFileHeader(String value, String prefix) {
        String path = value;
        if (path.startsWith("\"")) {
            path = readQuotedToken(path, 0).value();
        } else {
            int timestamp = path.indexOf('\t');
            if (timestamp >= 0) {
                path = path.substring(0, timestamp);
            }
        }
        if ("/dev/null".equals(path)) {
            return null;
        }
        return stripPrefix(path, prefix);
    }

    private static String stripPrefix(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            throw new IllegalStateException("GitHub returned an invalid pull request diff path.");
        }
        return path.substring(prefix.length());
    }

    private static Token readQuotedToken(String value, int start) {
        if (start >= value.length() || value.charAt(start) != '"') {
            throw new IllegalStateException("GitHub returned an invalid quoted diff path.");
        }
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        for (int index = start + 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                try {
                    String path = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(decoded.toByteArray()))
                            .toString();
                    return new Token(path, index + 1);
                } catch (CharacterCodingException e) {
                    throw new IllegalStateException("GitHub returned an invalid UTF-8 diff path.");
                }
            }
            if (character == '\\') {
                if (++index >= value.length()) {
                    break;
                }
                char escaped = value.charAt(index);
                if (escaped >= '0' && escaped <= '7') {
                    int octal = escaped - '0';
                    int digits = 1;
                    while (digits < 3 && index + 1 < value.length()
                            && value.charAt(index + 1) >= '0'
                            && value.charAt(index + 1) <= '7') {
                        octal = (octal * 8) + value.charAt(++index) - '0';
                        digits++;
                    }
                    decoded.write(octal);
                } else {
                    char decodedCharacter = switch (escaped) {
                        case 't' -> '\t';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        default -> escaped;
                    };
                    decoded.writeBytes(String.valueOf(decodedCharacter)
                            .getBytes(StandardCharsets.UTF_8));
                }
            } else {
                int codePoint = value.codePointAt(index);
                decoded.writeBytes(new String(Character.toChars(codePoint))
                        .getBytes(StandardCharsets.UTF_8));
                index += Character.charCount(codePoint) - 1;
            }
        }
        throw new IllegalStateException("GitHub returned an invalid quoted diff path.");
    }

    private static int skipSpaces(String value, int index) {
        while (index < value.length() && value.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private record Token(String value, int end) {
    }

    public record FileChanges(
            String oldPath,
            String newPath,
            List<LineRange> addedLines,
            List<LineRange> deletedLines,
            boolean binary) {

        public boolean hasAddedLine(int line) {
            return addedLines.stream().anyMatch(range -> range.contains(line));
        }

        public boolean hasDeletedLine(int line) {
            return deletedLines.stream().anyMatch(range -> range.contains(line));
        }
    }

    public record LineRange(int start, int end) {
        public boolean contains(int line) {
            return line >= start && line <= end;
        }
    }

    private static final class MutableFile {
        private String oldPath;
        private String newPath;
        private boolean binary;
        private final List<Integer> addedLines = new ArrayList<>();
        private final List<Integer> deletedLines = new ArrayList<>();

        private MutableFile(String oldPath, String newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
        }

        private FileChanges build() {
            return new FileChanges(
                    oldPath, newPath, toRanges(addedLines), toRanges(deletedLines), binary);
        }

        private static List<LineRange> toRanges(List<Integer> lines) {
            if (lines.isEmpty()) {
                return List.of();
            }
            List<LineRange> ranges = new ArrayList<>();
            int start = lines.getFirst();
            int end = start;
            for (int index = 1; index < lines.size(); index++) {
                int line = lines.get(index);
                if (line == end + 1) {
                    end = line;
                } else {
                    ranges.add(new LineRange(start, end));
                    start = line;
                    end = line;
                }
            }
            ranges.add(new LineRange(start, end));
            return List.copyOf(ranges);
        }
    }
}
