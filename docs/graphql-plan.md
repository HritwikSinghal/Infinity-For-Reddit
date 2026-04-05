# Plan: Add GraphQL (GQL) Support from Fork

## Context

Reddit's paid API changes broke many third-party apps. A reference fork had already implemented GraphQL support via Reddit's `gql-fed.reddit.com` federated endpoint, allowing authenticated users to use the GQL API while anonymous users continue using the REST `.json` endpoints. This plan ports **only the GQL-related changes** from that fork into this codebase (`~/Projects/Infinity-For-Reddit`), ignoring unrelated changes (Matrix chat, Giphy, dependency downgrades, removed features, etc.).

**Architecture pattern:** Dual-path routing — authenticated users → GQL, anonymous users → REST. No new library dependencies needed; GQL is implemented via manual JSON request/response construction over Retrofit.

---

## Changes by Layer

### Layer 1: New Files (2 files)

**1.1** Create `apis/GqlAPI.java`
- Retrofit interface with `@POST("/")` methods for all GQL operations
- Methods: `getSubredditBestPostsOauthListenableFuture`, `getUserPostsOauthListenableFuture`, `getBestPostsListenableFuture`, `searchPostsOauthListenableFuture`, `getPostComments`, `subredditAutocomplete`, `getSubredditData`, `subredditSubscription`, `updatePostVoteState`, `updateCommentVoteState`, `savePost`, `saveComment`, `searchUsers`, `searchSubreddit`
- Source: Copy from fork's `~/Projects/.../apis/GqlAPI.java`, remove `ChatOverviewActivity` import (not present in this repo)
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/apis/GqlAPI.java`

**1.2** Create `apis/GqlRequestBody.java`
- Static methods to construct GraphQL request bodies with persisted query hashes
- Operations: `subredditAutocompleteBody`, `subredditSearchBody`, `subredditDataBody`, `subscribeBody`, `updatePostVoteStateBody`, `updateCommentVoteStateBody`
- Helper: `createExtensionsObject(sha256Hash)`, `getUnixTime(timestamp)`
- Source: Copy from fork's `~/Projects/.../apis/GqlRequestBody.java`
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/apis/GqlRequestBody.java`

### Layer 2: Infrastructure (2 files modified)

**2.1** `utils/APIUtils.java` — Add constants
- Add `GQL_BASE_URL = "https://gql-fed.reddit.com"`
- Add `VOTESTATE_UP = "UP"`, `VOTESTATE_NONE = "NONE"`, `VOTESTATE_DOWN = "DOWN"`
- Add `ACTION_SUB = "SUBSCRIBED"`, `ACTION_UNSUB = "NONE"`
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/utils/APIUtils.java`

**2.2** `NetworkModule.java` — Add GQL Retrofit provider
- Add `@Provides @Named("gql") static Retrofit provideGqlRetrofit(...)` method
- Base URL: `APIUtils.GQL_BASE_URL`
- Uses `@Named("default")` OkHttpClient (same auth as OAuth)
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/NetworkModule.java`

### Layer 3: Parsers — New GQL Parsing Methods (3 files modified)

**3.1** `post/ParsePost.java` — Add GQL post parsing
- Add `parsePostsSyncGQL(response, nPosts, postFilter, readPostList)` — parses GQL post feed response (`data.postFeed.elements.edges[]` or `data.search.general.posts.edges[]`)
- Add `getLastItemGQL(response)` — extracts cursor from `pageInfo.endCursor`
- Add `parseBasicDataGQL(JSONObject data)` — extracts post fields from GQL schema (different field names: `authorInfo.name`, `createdAt` as ISO8601, `voteState` as string enum, `commentCount`, `isHidden`/`isSpoiler`/`isNsfw`/etc., `media.still` for previews, `crosspostRoot.post` for crossposts)
- Add `parseDataGQL(...)` — determines post type via `isSelfPost` boolean and `postHint` string (IMAGE, HOSTED_VIDEO, RICH_VIDEO, LINK) instead of URL heuristics. Uses `media.streaming.dashUrl` for video, `media.animated.mp4_source` for GIFs, `gallery.items[]` for galleries
- Add `setText(Post, JSONObject)` — extracts self text from `content.markdown`/`content.html`
- Add `insertImages(content, richtextMedia)` — resolves inline image references in markdown
- Add `getUnixTime(timestamp)` — parses ISO8601 timestamps to unix millis
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/post/ParsePost.java`

**3.2** `comment/ParseComment.java` — Add GQL comment parsing
- Add `parseCommentGQL(executor, handler, response, authorName, expandChildren, listener)` — parses `data.postInfoById.commentForest.trees[]`
- Add `parseMoreCommentGQL(...)` — parses "load more" GQL comment responses
- Add `parseCommentRecursionGQL(comments, newComments, moreChildrenIds, postId, subredditName, authorName)` — flat `trees[]` array with `__typename` discrimination (Comment vs DeletedComment vs hidden-child/more-cursor)
- Add `parseMoreCommentRecursionGQL(...)` — same as above but for "more comments" pagination
- Add `parseSingleCommentGQL(data, postId, subredditName, authorName)` — extracts comment from GQL node (different fields: `node.authorInfo.name`, `node.content.markdown`/`html`, `node.voteState`, `node.createdAt`, `node.isScoreHidden`, `node.isSaved`, `node.isInitiallyCollapsed`, `node.awardings[]`)
- Add `createDeletedComment(data, id, postId, subredditName)` — creates placeholder for deleted comments
- **Note:** Current repo's `parseComment()` takes a `CommentFilter` param that the fork doesn't have. The GQL parsing methods won't use CommentFilter initially (can be added later).
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/comment/ParseComment.java`

**3.3** `subreddit/ParseSubredditData.java` — Add GQL subreddit parsing
- Modify `ParseSubredditDataAsyncTask.doInBackground()` to parse GQL format: `data.subredditInfoByName` with nested `styles` object for icon/banner
- Add `parseSubredditDataSingle()` for single subreddit detail (GQL response shape)
- Modify `ParseSubredditListingDataAsyncTask` to parse GQL search response: `data.search.general.communities.edges[]` and `data.search.typeaheadByType.subreddits[]`
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/subreddit/ParseSubredditData.java`

### Layer 4: Fetch/Routing — Dual-Path Logic (4 files modified)

**4.1** `comment/FetchComment.java` — Add GQL routing for comments
- Add `GqlAPI gqlAPI` creation from retrofit
- `fetchComments()`: When `accessToken != null` and `commentId == null`, use `gqlAPI.getPostComments()` with `createCommentVariables()` and route response to `ParseComment.parseCommentGQL()`
- `fetchMoreComment()`: When `accessToken != null`, use GQL for "load more" comments
- Add private `createCommentVariables(id, sortType, afterKey)` — builds `PostComments` GQL operation body
- Add private `createExtensionsObject(sha256Hash)` helper
- **Note:** Current repo passes `CommentFilter` and `accountName`; the GQL path needs `authorName` instead. Pass the post author name through the call chain.
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/comment/FetchComment.java`

**4.2** `subreddit/FetchSubredditData.java` — Add GQL routing for subreddit data
- `fetchSubredditData()`: Change OAuth path from `RedditAPI` to `GqlAPI.getSubredditData()` with `GqlRequestBody.subredditDataBody()`
- `fetchSubredditListingData()`: Use `GqlAPI.searchSubreddit()` with `GqlRequestBody.subredditSearchBody()`
- **Signature change:** `fetchSubredditData()` needs both `oauthRetrofit` (GQL) and `retrofit` (REST fallback) params
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/subreddit/FetchSubredditData.java`

**4.3** `thing/VoteThing.java` — Switch to GQL for voting
- Add `isPost(String id)` helper to distinguish `t3_` (post) from `t1_` (comment)
- Replace `RedditAPI.voteThing()` with `GqlAPI.updatePostVoteState()`/`updateCommentVoteState()` using `GqlRequestBody` bodies
- Both overloads of `voteThing()` need updating
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/thing/VoteThing.java`

**4.4** `subreddit/SubredditSubscription.java` — Switch to GQL for subscriptions
- `subredditSubscription()`: Use `GqlAPI.subredditSubscription()` with `GqlRequestBody.subscribeBody(subredditId, action)`
- **Signature change:** Methods need `subredditId` param (GQL requires Reddit's internal ID, not just the name)
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/subreddit/SubredditSubscription.java`

### Layer 5: PostPagingSource + ViewModel Plumbing (2 files modified)

**5.1** `post/PostPagingSource.java` — Major changes
- Add `Retrofit gqlRetrofit` field to all constructors
- Add `transformDataGQL(Response<String>)` method — uses `ParsePost.parsePostsSyncGQL()` and `ParsePost.getLastItemGQL()`
- Add GQL request body builders: `createHomePostsVars()`, `createSubredditPostsVars()`, `createUserPostsVariables()`, `createSearchPostsVars()`, `createExtensionsObject()`
- Modify `loadFuture()` — create `GqlAPI` from `gqlRetrofit` when available, pass to load methods
- Modify `loadHomePosts()` — when GQL available, use `gqlAPI.getBestPostsListenableFuture()` + `transformDataGQL`
- Modify `loadSubredditPosts()` — when GQL available (except r/popular, r/all), use `gqlAPI.getSubredditBestPostsOauthListenableFuture()` + `transformDataGQL`
- Modify `loadUserPosts()` — when GQL available and `where == submitted`, use `gqlAPI.getUserPostsOauthListenableFuture()` + `transformDataGQL`
- Modify `loadSearchPosts()` — when authenticated, use `gqlAPI.searchPostsOauthListenableFuture()` + `transformDataGQL` (except user profile searches)
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/post/PostPagingSource.java`

**5.2** `post/PostViewModel.java` — Thread GQL Retrofit through
- Add `Retrofit gqlRetrofit` field
- Pass `gqlRetrofit` to all `PostPagingSource` constructor calls
- Update all Factory constructors to accept `gqlRetrofit`
- File: `app/src/main/java/ml/docilealligator/infinityforreddit/post/PostViewModel.java`

### Layer 6: DI Injection Updates (10+ files modified)

All classes that inject `@Named("oauth") Retrofit` and use it for voting, comments, subreddits, or posts now also need `@Named("gql") Retrofit` injected and passed through.

**Adapters (pass GQL Retrofit for voting):**
- `adapters/PostRecyclerViewAdapter.java` — VoteThing calls
- `adapters/PostDetailRecyclerViewAdapter.java` — VoteThing calls
- `adapters/CommentsRecyclerViewAdapter.java` — VoteThing + FetchComment calls
- `adapters/CommentsListingRecyclerViewAdapter.java` — VoteThing calls
- `adapters/SubredditListingRecyclerViewAdapter.java` — SubredditSubscription calls

**Fragments (inject and pass GQL Retrofit):**
- `fragments/PostFragment.java` — Creates PostViewModel
- `fragments/ViewPostDetailFragment.java` — FetchComment calls
- `fragments/CommentsListingFragment.java` — Creates CommentsListingViewModel
- `fragments/SubredditListingFragment.java` — Creates SubredditListingViewModel
- `fragments/SidebarFragment.java` — FetchSubredditData calls
- `fragments/HistoryPostFragment.java` — Creates PostViewModel

**Activities (inject and pass GQL Retrofit):**
- `activities/ViewSubredditDetailActivity.java` — SubredditSubscription + FetchSubredditData
- `activities/SearchActivity.java` — Creates PostViewModel for search
- Plus any other activities that create PostViewModels

---

## Execution Order

1. **Layer 1** — Create `GqlAPI.java` and `GqlRequestBody.java` (no dependencies, can go first)
2. **Layer 2** — Add constants to `APIUtils.java`, add GQL provider to `NetworkModule.java`
3. **Layer 3** — Add GQL parsing methods to `ParsePost`, `ParseComment`, `ParseSubredditData`
4. **Layer 4** — Add dual-path routing in `FetchComment`, `FetchSubredditData`, `VoteThing`, `SubredditSubscription`
5. **Layer 5** — Update `PostPagingSource` and `PostViewModel` with GQL support
6. **Layer 6** — Update DI injection in all fragments/activities/adapters

Each layer builds on the previous. Layers 1-3 are additive (no behavior change). Layer 4+ starts changing behavior for authenticated users.

---

## Key Adaptation Notes

| Fork assumption | Current repo reality | Adaptation needed |
|---|---|---|
| `SortType` in root package | `thing.SortType` | Use correct import |
| `VoteThing` in root package | `thing.VoteThing` | Modify in place |
| No `CommentFilter` | `CommentFilter` exists | Keep CommentFilter in REST path, skip in GQL path initially |
| `accessToken == null` for anon | `Account.ANONYMOUS_ACCOUNT` check | Use existing anon detection pattern |
| `FetchComment` takes `authorName` | Current takes `accountName` | Add `authorName` param for GQL path |
| `SubredditSubscription` takes `subredditId` | Current doesn't have `subredditId` | Need to pass `subredditId` from UI (SubredditData stores it) |
| `FetchSubredditData.fetchSubredditData()` takes 2 Retrofits | Current takes 1 Retrofit + accessToken | Update signature to take GQL Retrofit |

## Verification

1. Build the project: `./gradlew assembleDebug`
2. Test anonymous browsing (should use REST, unchanged behavior)
3. Test logged-in browsing (should use GQL for posts, comments, voting, subreddit data)
4. Test subreddit search (GQL)
5. Test voting on posts and comments (GQL)
6. Test subscribe/unsubscribe (GQL)
7. Test r/popular and r/all (should fall back to REST even when logged in)
8. Test "load more comments" (GQL)
9. Test search within subreddit (GQL)
10. Check logcat for `ParsePostGQL` debug logs confirming GQL parsing

## Risks

- **Persisted query hashes** in `GqlRequestBody` may become stale if Reddit rotates them. This is an inherent limitation of the approach — hashes would need periodic updates.
- **Missing GQL fields** (marked with TODOs in fork): sidebar description, suggested comment sort, deleted/removed post detection. These degrade gracefully to empty/null.
- **CommentFilter not applied in GQL path** initially — can be added as a follow-up.
- **SubredditSubscription needs subredditId** — the UI needs to pass this from `SubredditData.getId()`. All call sites need audit.
