import { Check, Copy, Share2 } from "lucide-react";
import { useState } from "react";
import { copyReviewLink, createReviewShareDetails, shareReviewLink } from "../utils/reviewSharing";

export default function ReviewShareButton({ review }) {
  const [feedback, setFeedback] = useState(null);
  const details = createReviewShareDetails(review, window.location.origin);

  if (!details) return null;

  async function handleShare() {
    setFeedback(null);
    try {
      const result = await shareReviewLink(details, navigator);
      if (result === "shared") setFeedback({ type: "success", message: "Review link shared." });
      if (result === "copied") setFeedback({ type: "success", message: "Review link copied to clipboard." });
      if (result === "cancelled") setFeedback({ type: "status", message: "Sharing canceled." });
    } catch {
      setFeedback({ type: "error", message: "Unable to share the review link. Try copying it instead." });
    }
  }

  async function handleCopy() {
    setFeedback(null);
    try {
      await copyReviewLink(details, navigator.clipboard);
      setFeedback({ type: "success", message: "Review link copied to clipboard." });
    } catch {
      setFeedback({ type: "error", message: "Unable to copy the review link. Check your browser clipboard permissions." });
    }
  }

  return (
    <div className="review-share">
      <div className="review-share-actions">
        <button className="secondary-button review-share-button" type="button" onClick={handleShare}>
          <Share2 size={16} />
          Share
        </button>
        <button className="secondary-button review-share-button" type="button" onClick={handleCopy}>
          {feedback?.message === "Review link copied to clipboard." ? <Check size={16} /> : <Copy size={16} />}
          Copy link
        </button>
      </div>
      {feedback && (
        <p className={`review-share-feedback review-share-${feedback.type}`} role={feedback.type === "error" ? "alert" : "status"}>
          {feedback.message}
        </p>
      )}
    </div>
  );
}
