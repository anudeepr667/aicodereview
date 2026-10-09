export const stats = [
  {
    title: "Total Reviews",
    value: "24",
    change: "+12%",
  },
  {
    title: "Open Pull Requests",
    value: "7",
    change: "+3",
  },
  {
    title: "Issues Found",
    value: "47",
    change: "-8%",
  },
  {
    title: "Average Code Score",
    value: "82",
    change: "+5%",
  },
];

export const pullRequests = [
  {
    id: 24,
    title: "Add JWT authentication",
    repository: "banking-api",
    author: "Anudeep",
    score: 78,
    issues: 6,
    status: "Reviewed",
  },
  {
    id: 23,
    title: "Fix payment validation bug",
    repository: "payment-service",
    author: "Rahul",
    score: 91,
    issues: 2,
    status: "Reviewed",
  },
  {
    id: 22,
    title: "Refactor user service",
    repository: "user-service",
    author: "Kiran",
    score: 86,
    issues: 4,
    status: "Reviewed",
  },
];

export const codeQuality = [
  {
    name: "Security",
    score: 84,
  },
  {
    name: "Maintainability",
    score: 78,
  },
  {
    name: "Performance",
    score: 91,
  },
  {
    name: "Reliability",
    score: 88,
  },
];

export const issueSeverity = [
  {
    name: "Critical",
    count: 2,
  },
  {
    name: "High",
    count: 7,
  },
  {
    name: "Medium",
    count: 14,
  },
  {
    name: "Low",
    count: 24,
  },
];
export const reviewDetails = [
  {
    id: 24,
    title: "Add JWT authentication",
    repository: "banking-api",
    branch: "feature/jwt-auth",
    author: "Anudeep",
    score: 78,
    status: "Reviewed",

    changedFiles: 5,
    additions: 142,
    deletions: 36,

    summary:
      "The authentication implementation is generally good, but there are security and maintainability issues that should be fixed before merging.",

    findings: [
      {
        id: 1,
        severity: "High",
        type: "Security",
        file: "AuthService.java",
        line: 48,
        title: "JWT secret is hardcoded",
        description:
          "Move the JWT secret into an environment variable or secure configuration instead of storing it directly in source code.",
      },
      {
        id: 2,
        severity: "Medium",
        type: "Maintainability",
        file: "JwtService.java",
        line: 32,
        title: "Token expiration value is hardcoded",
        description:
          "Move the expiration duration into application configuration so it can be changed without modifying source code.",
      },
      {
        id: 3,
        severity: "Low",
        type: "Code Quality",
        file: "AuthController.java",
        line: 27,
        title: "Method name could be clearer",
        description:
          "Consider renaming the method to better communicate that it authenticates a user and returns a JWT token.",
      },
    ],

    code: `public String generateToken(User user) {
    String secret = "my-secret-key";

    return Jwts.builder()
        .subject(user.getEmail())
        .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
        .compact();
}`,
  },
];