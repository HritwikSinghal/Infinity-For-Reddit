# GQL Routing Architecture

## 1. What does GQL actually do for each user type?

**Authenticated users — GQL is essential.** Reddit broke/rate-limited their REST API for authenticated third-party tokens (the paid API changes). GQL via `gql-fed.reddit.com` bypasses this because it's the same internal endpoint Reddit's own apps use. Without GQL, authenticated users would hit API limits or get blocked.

**Anonymous users — GQL gives them nothing.** The unauthenticated REST `.json` endpoints still work fine. Every routing decision in the code confirms this:

```java
// PostPagingSource.java — anonymous always hits REST
if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
    subredditPost = api.getSubredditBestPostsListenableFuture(...)  // REST
}
```

## 2. Will non-auth users ever use GQL?

No. The architecture is explicitly `auth → GQL`, `anonymous → REST`. Looking at the actual codebase right now, even among authenticated operations only voting (`VoteThing.java`) actually routes through GQL. Post fetching, comments, subreddit data, and subscriptions are all still REST — the GQL parsers are written but the routing in `PostPagingSource`, `FetchComment`, `FetchSubredditData`, and `SubredditSubscription` hasn't been wired up yet.

### Current wiring state

| Operation | Status | File |
|-----------|--------|------|
| Voting | **GQL (wired)** | `thing/VoteThing.java` |
| Post fetching | REST only | `post/PostPagingSource.java` |
| Comment fetching | REST only | `comment/FetchComment.java` |
| Subreddit data | REST only | `subreddit/FetchSubredditData.java` |
| Subreddit subscription | REST only | `subreddit/SubredditSubscription.java` |

Parsers for GQL post/comment data exist (`ParsePost`, `ParseComment`) but are dead code until the routing in Layer 4–5 is connected.

## 3. How do anonymous users see NSFW if Reddit locked it behind login?

See [`nsfw-handling.md`](nsfw-handling.md) for the full breakdown. Short answer: Reddit's "NSFW requires login" is a web UI restriction only — a JavaScript modal on `reddit.com`. The API never enforced it. Any valid app token (anonymous or authenticated) returns NSFW content.
