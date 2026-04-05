# Tasks
Porting GraphQL support from fork (`~/Projects/Infinity-For-Reddit`) into this repo. See `GRAPHQL.md` for full plan.

## Completed

- [x] **Layer 1.1** — Created `apis/GqlAPI.java` (Retrofit interface, 16 `@POST("/")` methods)
- [x] **Layer 1.2** — Created `apis/GqlRequestBody.java` (request body builders + persisted query hashes)
- [x] **Layer 2.1** — Added constants to `utils/APIUtils.java` (`GQL_BASE_URL`, `VOTESTATE_*`, `ACTION_SUB/UNSUB`)
- [x] **Layer 2.2** — Added `@Named("gql") Retrofit` provider to `NetworkModule.java`
- [x] **Layer 4.3** — Switched `thing/VoteThing.java` from REST to GQL (uses `GqlAPI` + `GqlRequestBody`)

## Layer 3: Parsers — GQL Parsing Methods

### 3.1 — `post/ParsePost.java`
- [ ] Add `parsePostsSyncGQL()` — parses `data.postFeed.elements.edges[]` or `data.search.general.posts.edges[]`
- [ ] Add `getLastItemGQL()` — cursor from `pageInfo.endCursor`/`hasNextPage`
- [ ] Add `parseBasicDataGQL()` — GQL field mapping (see fork line 351-502)
- [ ] Add `parseDataGQL()` — post type via `isSelfPost`/`postHint` instead of URL heuristics (see fork line 968-1231)
- [ ] Add `setText()` helper — `content.markdown`/`content.html`
- [ ] Add `insertImages()` — resolves `![img](id)` references via `richtextMedia` array
- [ ] Add `getUnixTime()` — ISO8601 → unix millis
- **Reference:** Fork file `~/Projects/.../post/ParsePost.java` lines 77-1322

### 3.2 — `comment/ParseComment.java`
- [ ] Add `parseCommentGQL()` — entry point, parses `data.postInfoById.commentForest.trees[]`
- [ ] Add `parseMoreCommentGQL()` — for "load more" responses
- [ ] Add `parseCommentRecursionGQL()` — flat tree with `__typename` discrimination
- [ ] Add `parseMoreCommentRecursionGQL()` — same but for pagination
- [ ] Add `parseSingleCommentGQL()` — single comment from GQL node
- [ ] Add `createDeletedComment()` — placeholder for deleted comments
- **Note:** GQL methods skip `CommentFilter` for now (REST path keeps it)
- **Reference:** Fork file `~/Projects/.../comment/ParseComment.java` lines 61-719

### 3.3 — `subreddit/ParseSubredditData.java`
- [ ] Add `parseSubredditDataSingle()` — GQL single subreddit (`data.subredditInfoByName`, nested `styles` for icon/banner)
- [ ] Modify `ParseSubredditDataAsyncTask` — parse GQL format
- [ ] Modify `ParseSubredditListingDataAsyncTask` — parse `data.search.general.communities.edges[]` and `data.search.typeaheadByType.subreddits[]`
- **Reference:** Fork file `~/Projects/.../subreddit/ParseSubredditData.java`

## Layer 4: Fetch/Routing — Dual-Path Logic

### 4.1 — `comment/FetchComment.java`
- [ ] Add GQL path: when `accessToken != null && commentId == null` → `gqlAPI.getPostComments()` → `ParseComment.parseCommentGQL()`
- [ ] Add GQL path for `fetchMoreComment()`: when authenticated → GQL "load more"
- [ ] Add `createCommentVariables(id, sortType, afterKey)` — builds `PostComments` GQL operation body
- [ ] Add `createExtensionsObject()` helper
- **Signature change needed:** Add `authorName` param (GQL needs it for `isSubmitter` detection; current repo only has `accountName`)
- **Reference:** Fork file `~/Projects/.../comment/FetchComment.java`

### 4.2 — `subreddit/FetchSubredditData.java`
- [ ] `fetchSubredditData()`: authenticated → `GqlAPI.getSubredditData()` + `GqlRequestBody.subredditDataBody()`
- [ ] `fetchSubredditListingData()`: → `GqlAPI.searchSubreddit()` + `GqlRequestBody.subredditSearchBody()`
- **Signature change:** needs both `oauthRetrofit` (GQL) and `retrofit` (REST) params
- **Callers to update:** `SidebarFragment`, `ViewSubredditDetailActivity`, `SubredditSubscription`
- **Reference:** Fork file `~/Projects/.../subreddit/FetchSubredditData.java`

### 4.4 — `subreddit/SubredditSubscription.java`
- [ ] Switch `subredditSubscription()` to use `GqlAPI.subredditSubscription()` + `GqlRequestBody.subscribeBody()`
- **Signature change:** methods need `subredditId` param (GQL uses internal ID, not name)
- **Callers to update:** `SubredditListingRecyclerViewAdapter`, `ViewSubredditDetailActivity`
- **Reference:** Fork file `~/Projects/.../subreddit/SubredditSubscription.java`

## Layer 5: PostPagingSource + ViewModel Plumbing

### 5.1 — `post/PostPagingSource.java`
- [ ] Add `Retrofit gqlRetrofit` field to all 4 constructors
- [ ] Add `transformDataGQL()` — uses `parsePostsSyncGQL()` + `getLastItemGQL()`
- [ ] Add `createHomePostsVars()` — `HomeElements` GQL operation (hash: `fc940c3f...`)
- [ ] Add `createSubredditPostsVars()` — `SubredditFeedElements` GQL operation (hash: `dc87cffd...`)
- [ ] Add `createUserPostsVariables()` — `UserSubmittedPostSets` GQL operation (hash: `6b5c7d94...`)
- [ ] Add `createSearchPostsVars()` — `SearchPosts` GQL operation (hash: `0d65275f...`)
- [ ] Modify `loadFuture()` — pass `GqlAPI` to load methods
- [ ] Modify `loadHomePosts()` — GQL when authenticated
- [ ] Modify `loadSubredditPosts()` — GQL except r/popular, r/all
- [ ] Modify `loadUserPosts()` — GQL when `where == submitted`
- [ ] Modify `loadSearchPosts()` — GQL when authenticated (except u_ searches)
- **Reference:** Fork file `~/Projects/.../post/PostPagingSource.java`

### 5.2 — `post/PostViewModel.java`
- [ ] Add `Retrofit gqlRetrofit` field
- [ ] Pass `gqlRetrofit` to all `PostPagingSource` constructors
- [ ] Update all `Factory` inner classes to accept `gqlRetrofit`
- **Reference:** Fork file `~/Projects/.../post/PostViewModel.java`

## Layer 6: DI Injection — Thread `@Named("gql") Retrofit`

### Adapters (need GQL Retrofit for VoteThing)
- [ ] `adapters/PostRecyclerViewAdapter.java`
- [ ] `adapters/PostDetailRecyclerViewAdapter.java`
- [ ] `adapters/CommentsRecyclerViewAdapter.java` (VoteThing + FetchComment)
- [ ] `adapters/CommentsListingRecyclerViewAdapter.java`
- [ ] `adapters/SubredditListingRecyclerViewAdapter.java` (SubredditSubscription)

### Fragments (inject + pass GQL Retrofit)
- [ ] `fragments/PostFragment.java` — PostViewModel creation
- [ ] `fragments/ViewPostDetailFragment.java` — FetchComment calls
- [ ] `fragments/CommentsListingFragment.java`
- [ ] `fragments/SubredditListingFragment.java`
- [ ] `fragments/SidebarFragment.java` — FetchSubredditData
- [ ] `fragments/HistoryPostFragment.java` — PostViewModel creation

### Activities (inject + pass GQL Retrofit)
- [ ] `activities/ViewSubredditDetailActivity.java` — SubredditSubscription + FetchSubredditData
- [ ] `activities/SearchActivity.java` — PostViewModel for search
- [ ] Audit remaining activities that create PostViewModels

## Key Adaptation Notes

| Fork pattern | This repo pattern | What to do |
|---|---|---|
| `import ...SortType` (root pkg) | `import ...thing.SortType` | Use `thing.SortType` import |
| `VoteThing` in root pkg | `thing.VoteThing` | Already modified in place |
| No `CommentFilter` | `CommentFilter` exists | GQL path skips it; REST path keeps it |
| `accessToken == null` = anon | `Account.ANONYMOUS_ACCOUNT` check | Use `accountName.equals(Account.ANONYMOUS_ACCOUNT)` |
| `FetchComment(authorName)` | `FetchComment(accountName)` | Add `authorName` param alongside `accountName` |
| `SubredditSubscription(subredditId)` | No `subredditId` param | Add param; `SubredditData.getId()` has it |
| `FetchSubredditData(oauthRetrofit, retrofit)` | `FetchSubredditData(retrofit, accessToken)` | Add GQL retrofit param |

## Build Note

JDK toolchain mismatch: project expects Java compiler but `/usr/lib/jvm/java-21-openjdk` lacks `JAVA_COMPILER` capability. This is a pre-existing env issue, not caused by GQL changes. Fix by installing JDK 17 or adjusting `compileOptions` in `app/build.gradle`.
