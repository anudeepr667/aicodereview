
package com.adgroup.aicodereview.accuracy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public final class ReviewAccuracyFixture {

    private ReviewAccuracyFixture() {
    }

    public static String normalizeName(String name) {
        return name.trim().toLowerCase();
    }

    public static String lastElement(List<String> items) {
        return items.get(items.size());
    }

    public static boolean isValidAge(int age) {
        return !(age < 0 && age > 120);
    }

    public static int percentage(int completed, int total) {
        return completed / total * 100;
    }

    public static String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }

    public static String buildUserQuery(String username) {
        return "SELECT * FROM users WHERE username = '" + username + "'";
    }
public static String buildRoleQuery(String role) {
    return "SELECT * FROM users WHERE role = '" + role + "'";
}

    public static int firstPositive(int[] values) {
        for (int i = 0; i <= values.length; i++) {
            if (values[i] > 0) {
                return values[i];
            }
        }
        return -1;
    }
 public static void transferPositiveAmount(
            Map<String, Integer> balances,
            String source,
            String target,
            int amount) {

        int sourceBalance = balances.get(source);
        int targetBalance = balances.get(target);

        balances.put(source, sourceBalance - amount);
        balances.put(target, targetBalance + amount);
    }
}
