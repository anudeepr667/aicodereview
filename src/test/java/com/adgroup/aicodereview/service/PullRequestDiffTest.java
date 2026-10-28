package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.github.PullRequestDiff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullRequestDiffTest {

    @Test
    void parsesAddedAndDeletedLineRangesForRenamedFiles() {
        String diff = """
                diff --git a/src/Old.java b/src/New.java
                similarity index 70%
                rename from src/Old.java
                rename to src/New.java
                --- a/src/Old.java
                +++ b/src/New.java
                @@ -4,2 +4,2 @@
                 context
                -old code
                +new code
                """;

        PullRequestDiff.FileChanges file = PullRequestDiff.parse(diff).files().getFirst();

        assertEquals("src/Old.java", file.oldPath());
        assertEquals("src/New.java", file.newPath());
        assertTrue(file.hasDeletedLine(5));
        assertTrue(file.hasAddedLine(5));
    }

    @Test
    void parsesAddedAndDeletedFilesAndQuotedPaths() {
        String diff = """
                diff --git "a/src/Old File.java" "b/src/New File.java"
                --- "a/src/Old File.java"
                +++ "b/src/New File.java"
                @@ -1 +1 @@
                -old
                +new
                diff --git a/src/Added.java b/src/Added.java
                --- /dev/null
                +++ b/src/Added.java
                @@ -0,0 +1,1 @@
                +added
                diff --git a/src/Deleted.java b/src/Deleted.java
                --- a/src/Deleted.java
                +++ /dev/null
                @@ -1,1 +0,0 @@
                -deleted
                diff --git a/assets/logo.png b/assets/logo.png
                index 123..456 100644
                Binary files a/assets/logo.png and b/assets/logo.png differ
                """;

        var files = PullRequestDiff.parse(diff).files();

        assertEquals("src/Old File.java", files.get(0).oldPath());
        assertEquals("src/New File.java", files.get(0).newPath());
        assertNull(files.get(1).oldPath());
        assertEquals("src/Added.java", files.get(1).newPath());
        assertEquals(1, files.get(1).addedLines().getFirst().start());
        assertEquals("src/Deleted.java", files.get(2).oldPath());
        assertNull(files.get(2).newPath());
        assertEquals(1, files.get(2).deletedLines().getFirst().start());
        assertTrue(files.get(3).binary());
    }

    @Test
    void decodesGitOctalEscapesInUtf8Paths() {
        String diff = "diff --git \"a/src/\\303\\251.java\" \"b/src/\\303\\251.java\"\n"
                + "--- \"a/src/\\303\\251.java\"\n"
                + "+++ \"b/src/\\303\\251.java\"\n"
                + "@@ -0,0 +1,1 @@\n"
                + "+class Example {}\n";

        PullRequestDiff.FileChanges file = PullRequestDiff.parse(diff).files().getFirst();

        assertEquals("src/é.java", file.newPath());
    }
}
