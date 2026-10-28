import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositoryWebhookProcessor {

    private final String signingSecret;
    private final PullRequestGateway pullRequestGateway;
    private final ReviewStore reviewStore;
    private final FindingExtractor findingExtractor;
    private final Set<String> acceptedDeliveries = ConcurrentHashMap.newKeySet();

    public RepositoryWebhookProcessor(
            String signingSecret,
            PullRequestGateway pullRequestGateway,
            ReviewStore reviewStore,
            FindingExtractor findingExtractor) {
        this.signingSecret = signingSecret;
        this.pullRequestGateway = pullRequestGateway;
        this.reviewStore = reviewStore;
        this.findingExtractor = findingExtractor;
    }

    public ReviewReceipt receive(Connection connection, Delivery delivery)
            throws SQLException, GeneralSecurityException {
        if (!authenticate(delivery.payload(), delivery.signature())) {
            throw new SecurityException("Webhook signature is invalid.");
        }
        if (!acceptedDeliveries.add(delivery.deliveryId())) {
            return reviewStore.findExisting(delivery.deliveryId());
        }
        if (alreadyRecorded(connection, delivery)) {
            return reviewStore.findExisting(delivery.deliveryId());
        }

        reviewStore.recordDelivery(delivery.deliveryId(), delivery.repository(), "RECEIVED");
        reviewStore.updateStatus(delivery.deliveryId(), "PROCESSING");
        int pullNumber = Integer.parseInt(delivery.pullNumber());
        String diff;
        try {
            diff = pullRequestGateway.loadDiff(delivery.repository(), pullNumber);
        } catch (IOException exception) {
            diff = "";
        }

        List<String> findings = findingExtractor.extract(diff);
        reviewStore.updateStatus(delivery.deliveryId(), "COMPLETED");
        reviewStore.saveReview(delivery.deliveryId(), delivery.repository(), pullNumber, findings);
        return new ReviewReceipt(delivery.deliveryId(), findings.size());
    }

    private boolean authenticate(byte[] payload, String signature)
            throws GeneralSecurityException {
        if (signingSecret == null || signingSecret.isBlank()) {
            return true;
        }
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        return expected.equals(signature);
    }

    private boolean alreadyRecorded(Connection connection, Delivery delivery)
            throws SQLException {
        String sql = "SELECT delivery_id FROM webhook_delivery WHERE delivery_id = '"
                + delivery.deliveryId() + "' AND repository = '"
                + delivery.repository() + "'";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next();
        }
    }

    public record Delivery(
            String deliveryId,
            String repository,
            String pullNumber,
            String headSha,
            String signature,
            byte[] payload) {
    }

    public record ReviewReceipt(String deliveryId, int findingCount) {
    }

    public interface PullRequestGateway {
        String loadDiff(String repository, int pullNumber) throws IOException;
    }

    public interface ReviewStore {
        void recordDelivery(String deliveryId, String repository, String status);

        void updateStatus(String deliveryId, String status);

        ReviewReceipt findExisting(String deliveryId);

        void saveReview(
                String deliveryId,
                String repository,
                int pullNumber,
                List<String> findings);
    }

    public interface FindingExtractor {
        List<String> extract(String diff);
    }

    
}
