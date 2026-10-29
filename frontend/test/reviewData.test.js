import assert from "node:assert/strict";
import { test } from "node:test";
import {
  buildCategoryReviews,
  REVIEW_CATEGORY_ORDER
} from "../src/utils/reviewData.js";

test("normalizes legacy bug categories, merges distinct explanations and renders only canonical sections", () => {
  const categories = buildCategoryReviews([
    { category: "Bugs & Logical Errors", review: " " },
    { category: "Bug & Logic Assessment", review: "Found a null handling issue." },
    { category: "Bugs & Logical Assessment", review: "Found a null handling issue." },
    { category: "Bugs & Logical Errors", review: "Also checked the changed control flow." },
    { category: "Security Vulnerabilities", review: "No security issue was identified." },
    { category: "Unexpected Extra Category", review: "Ignore fixed list" },
    { category: "Positive Aspects", review: "Not a review category" }
  ]);

  assert.deepEqual(categories.map(({ category }) => category), REVIEW_CATEGORY_ORDER);
  assert.equal(categories.length, 5);
  assert.equal(
    categories[0].review,
    "Found a null handling issue.\n\nAlso checked the changed control flow."
  );
  assert.equal(categories.filter(({ category }) => category === "Bugs & Logical Assessment").length, 1);
  assert.equal(categories[1].review, "No security issue was identified.");
  assert.equal(categories[2].review, "");
  assert.equal(categories[3].review, "");
  assert.equal(categories[4].review, "");
});

test("maps the previous best-practices label without adding an extra category", () => {
  const categories = buildCategoryReviews([
    { category: "Java and Framework Best Practices", review: "Prefer the existing service helper." }
  ]);

  assert.equal(categories.length, 5);
  assert.equal(categories[4].category, "Best Practices");
  assert.equal(categories[4].review, "Prefer the existing service helper.");
});
