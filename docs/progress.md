# Project: Infinity-For-Reddit GraphQL Port
> Last updated: 2026-04-06 | Session: 4

## Overview
Implementing GraphQL (GQL) support directly in this repo on the `gql` branch. Reddit's paid API changes broke REST for authenticated operations. GQL is added via `gql-fed.reddit.com` using a dual-path architecture: authenticated users route through GQL, anonymous users continue using REST `.json` endpoints. No new library dependencies -- GQL is implemented via manual JSON request/response construction over Retrofit. Full plan: `docs/graphql-plan.md`.

**Active project path:** `/home/hritwik/Projects/Infinity-For-Reddit`

## Plan

### Phase 1: New Files (Layer 1)
- [x] 1.1 Create `apis/GqlAPI.java` -- Retrofit interface, 16 `@POST("/")` methods
- [x] 1.2 Create `apis/GqlRequestBody.java` -- request body builders + persisted query hashes

### Phase 2: Infrastructure (Layer 2)
- [x] 2.1 `utils/APIUtils.java` -- add `GQL_BASE_URL`, `VOTESTATE_*`, `ACTION_SUB/UNSUB` constants
- [x] 2.2 `NetworkModule.java` -- add `@Named("gql") Retrofit` provider

### Phase 3: Parsers (Layer 3)
- [x] 3.1 `post/ParsePost.java` -- `parsePostsSyncGQL`, `getLastItemGQL`, `parseBasicDataGQL`, `parseDataGQL`, `setText`, `insertImages`, `getUnixTime`
- [x] 3.2 `comment/ParseComment.java` -- `parseCommentGQL`, `parseMoreCommentGQL`, `parseCommentRecursionGQL`, `parseMoreCommentRecursionGQL`, `parseSingleCommentGQL`, `createDeletedComment`
- [ ] 3.3 `subreddit/ParseSubredditData.java` -- `parseSubredditDataSingle`, modify `ParseSubredditDataAsyncTask`, modify `ParseSubredditListingDataAsyncTask`

### Phase 4: Fetch/Routing (Layer 4)
- [x] 4.3 `thing/VoteThing.java` -- switched REST to GQL for voting
- [ ] 4.1 `comment/FetchComment.java` -- GQL path for `fetchComments()` + `fetchMoreComment()`, add `createCommentVariables`, signature change (add `authorName`)
- [ ] 4.2 `subreddit/FetchSubredditData.java` -- GQL routing for subreddit data + listing, signature change (add GQL retrofit)
- [ ] 4.4 `subreddit/SubredditSubscription.java` -- switch to GQL, signature change (add `subredditId`)

### Phase 5: PostPagingSource + ViewModel (Layer 5)
- [ ] 5.1 `post/PostPagingSource.java` -- add `gqlRetrofit` to constructors, `transformDataGQL`, GQL body builders, modify `loadHomePosts`/`loadSubredditPosts`/`loadUserPosts`/`loadSearchPosts`
- [ ] 5.2 `post/PostViewModel.java` -- thread `gqlRetrofit` through all Factory classes

### Phase 6: DI Injection (Layer 6)
- [~] 6.1 Adapters -- `PostRecyclerViewAdapter`, `PostDetailRecyclerViewAdapter`, `CommentsRecyclerViewAdapter`, `CommentsListingRecyclerViewAdapter` have `@Named("gql")` for voting. `SubredditListingRecyclerViewAdapter` does NOT.
- [~] 6.2 Fragments -- `PostFragmentBase` (parent of `PostFragment`, `HistoryPostFragment`), `ViewPostDetailFragment`, `CommentsListingFragment` have `@Named("gql")` injection, but only pass it to adapters for voting. `SubredditListingFragment`, `SidebarFragment` do NOT have it.
- [ ] 6.3 Activities -- NO activities have `@Named("gql")` injection.

## Actual GQL usage (what's wired end-to-end)

Only **voting** is fully on GQL right now:
```
Fragment → injects @Named("gql") → passes to Adapter → Adapter calls VoteThing → VoteThing uses GqlAPI
```

Everything else (post fetching, comment fetching, subreddit data, subscriptions) is **still REST**. The GQL parsers are written (Phase 3) but the routing layer (Phases 4-5) hasn't been connected, so those parsers are dead code.

## Status Summary
| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: New Files | Done | 2/2 |
| Phase 2: Infrastructure | Done | 2/2 |
| Phase 3: Parsers | Partial | 2/3 (ParseSubredditData missing GQL) |
| Phase 4: Fetch/Routing | Partial | 1/4 (only VoteThing) |
| Phase 5: PostPagingSource + ViewModel | Not started | 0/2 |
| Phase 6: DI Injection | Partial | voting-only wiring in 4 adapters + 5 fragments |

## Decisions & Notes
2026-04-06: GQL path skips `CommentFilter` for now; REST path retains it. Can add later.
2026-04-06: Fork has `awards/nAwards` in Post constructor -- this repo does not. GQL path passes `false, false, false, 0L, null, false, false` for mod fields that exist in this repo but not the fork.
2026-04-06: JDK toolchain mismatch (Java 21 lacks `JAVA_COMPILER` capability) is pre-existing, not caused by GQL changes.
2026-04-06: Persisted query hashes in `GqlRequestBody` may go stale if Reddit rotates them -- inherent limitation.
2026-04-06: Anonymous users never use GQL. The dual-path architecture routes only authenticated users through GQL; anonymous users stay on REST `.json` endpoints with no auth token at all (direct hits to `www.reddit.com/*.json`). See `docs/nsfw-handling.md` for NSFW details.
2026-04-06: The fork uses `CLIENT_ID = "ohXpoqrZYub1kg"` (Reddit's official Android app ID). This repo uses `CLIENT_ID = "NOe2iKrPPzwscA"` (third-party app ID). Both get NSFW content from the API — the login wall is web-only.

## Blockers
- JDK toolchain mismatch prevents `./gradlew assembleDebug`. Need JDK 17 or `compileOptions` adjustment.
