# Project: Infinity-For-Reddit GraphQL Port
> Last updated: 2026-04-06 | Session: 2

## Overview
Porting GraphQL (GQL) support from the fork at `~/Projects/Infinity-For-Reddit` into this repo (`~/test/Infinity-For-Reddit`). Reddit's paid API changes broke REST for authenticated operations. The fork added GQL via `gql-fed.reddit.com` using a dual-path architecture: authenticated users route through GQL, anonymous users continue using REST `.json` endpoints. No new library dependencies -- GQL is implemented via manual JSON request/response construction over Retrofit. Branch: `gql`. Full plan: `doc/GRAPHQL.md`.

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
- [ ] 6.1 Adapters -- `PostRecyclerViewAdapter`, `PostDetailRecyclerViewAdapter`, `CommentsRecyclerViewAdapter`, `CommentsListingRecyclerViewAdapter`, `SubredditListingRecyclerViewAdapter`
- [ ] 6.2 Fragments -- `PostFragment`, `ViewPostDetailFragment`, `CommentsListingFragment`, `SubredditListingFragment`, `SidebarFragment`, `HistoryPostFragment`
- [ ] 6.3 Activities -- `ViewSubredditDetailActivity`, `SearchActivity`, audit remaining PostViewModel creators

## Status Summary
| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: New Files | Done | 2/2 |
| Phase 2: Infrastructure | Done | 2/2 |
| Phase 3: Parsers | In Progress | 2/3 |
| Phase 4: Fetch/Routing | In Progress | 1/4 |
| Phase 5: PostPagingSource + ViewModel | Pending | 0/2 |
| Phase 6: DI Injection | Pending | 0/3 |

## Decisions & Notes
2026-04-06: GQL path skips `CommentFilter` for now; REST path retains it. Can add later.
2026-04-06: Fork has `awards/nAwards` in Post constructor -- this repo does not. GQL path passes `false, false, false, 0L, null, false, false` for mod fields that exist in this repo but not the fork.
2026-04-06: JDK toolchain mismatch (Java 21 lacks `JAVA_COMPILER` capability) is pre-existing, not caused by GQL changes.
2026-04-06: Persisted query hashes in `GqlRequestBody` may go stale if Reddit rotates them -- inherent limitation.

## Blockers
- JDK toolchain mismatch prevents `./gradlew assembleDebug`. Need JDK 17 or `compileOptions` adjustment.
