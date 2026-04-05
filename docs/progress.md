# Project: Infinity-For-Reddit GraphQL Port
> Last updated: 2026-04-06 | Session: 3

## Overview
Implementing GraphQL (GQL) support directly in this repo on the `gql` branch. Reddit's paid API changes broke REST for authenticated operations. GQL is added via `gql-fed.reddit.com` using a dual-path architecture: authenticated users route through GQL, anonymous users continue using REST `.json` endpoints. No new library dependencies -- GQL is implemented via manual JSON request/response construction over Retrofit. Full plan: `docs/GRAPHQL.md`.

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
- [x] 3.3 `subreddit/ParseSubredditData.java` -- `parseSubredditDataSingle`, modify `ParseSubredditDataAsyncTask`, modify `ParseSubredditListingDataAsyncTask`

### Phase 4: Fetch/Routing (Layer 4)
- [x] 4.3 `thing/VoteThing.java` -- switched REST to GQL for voting
- [x] 4.1 `comment/FetchComment.java` -- GQL path for `fetchComments()` + `fetchMoreComment()`, add `createCommentVariables`, signature change (add `authorName`)
- [x] 4.2 `subreddit/FetchSubredditData.java` -- GQL routing for subreddit data + listing, signature change (add GQL retrofit)
- [x] 4.4 `subreddit/SubredditSubscription.java` -- switch to GQL, signature change (add `subredditId`)

### Phase 5: PostPagingSource + ViewModel (Layer 5)
- [x] 5.1 `post/PostPagingSource.java` -- add `gqlRetrofit` to constructors, `transformDataGQL`, GQL body builders, modify `loadHomePosts`/`loadSubredditPosts`/`loadUserPosts`/`loadSearchPosts`
- [x] 5.2 `post/PostViewModel.java` -- thread `gqlRetrofit` through all Factory classes

### Phase 6: DI Injection (Layer 6)
- [x] 6.1 Adapters -- `PostRecyclerViewAdapter`, `PostDetailRecyclerViewAdapter`, `CommentsRecyclerViewAdapter`, `CommentsListingRecyclerViewAdapter`, `SubredditListingRecyclerViewAdapter`
- [x] 6.2 Fragments -- `PostFragment`, `ViewPostDetailFragment`, `CommentsListingFragment`, `SubredditListingFragment`, `SidebarFragment`, `HistoryPostFragment`
- [x] 6.3 Activities -- `ViewSubredditDetailActivity`, `SearchActivity`, all injection targets registered in `AppComponent`

## Status Summary
| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: New Files | Done | 2/2 |
| Phase 2: Infrastructure | Done | 2/2 |
| Phase 3: Parsers | Done | 3/3 |
| Phase 4: Fetch/Routing | Done | 4/4 |
| Phase 5: PostPagingSource + ViewModel | Done | 2/2 |
| Phase 6: DI Injection | Done | 3/3 |

## Decisions & Notes
2026-04-06: GQL path skips `CommentFilter` for now; REST path retains it. Can add later.
2026-04-06: Fork has `awards/nAwards` in Post constructor -- this repo does not. GQL path passes `false, false, false, 0L, null, false, false` for mod fields that exist in this repo but not the fork.
2026-04-06: JDK toolchain mismatch (Java 21 lacks `JAVA_COMPILER` capability) is pre-existing, not caused by GQL changes.
2026-04-06: Persisted query hashes in `GqlRequestBody` may go stale if Reddit rotates them -- inherent limitation.

## Blockers
- JDK toolchain mismatch prevents `./gradlew assembleDebug`. Need JDK 17 or `compileOptions` adjustment.

## Decisions & Notes (Session 3)
2026-04-06: All 6 phases were already complete in `-kh` when session 3 began — progress.md was stale. Verified each file individually: ParseSubredditData (GQL AsyncTask), FetchComment (dual-path + createCommentVariables), FetchSubredditData (GQL dual-path), SubredditSubscription (GQL + subredditId), PostPagingSource (gqlRetrofit + all body builders), PostViewModel (gqlRetrofit threaded through all 4 Factory constructors). Phase 6 DI: all 16 files have @Named("gql") injection; SubredditListingFragment passes mGQLRetrofit as oauthRetrofit to SubredditListingRecyclerViewAdapter, which SubredditSubscription uses to call GqlAPI. All injection targets registered in AppComponent.
