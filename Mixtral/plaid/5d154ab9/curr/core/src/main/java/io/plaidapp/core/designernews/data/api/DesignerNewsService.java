package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.data.api.EnvelopePayload;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.login.model.AccessToken;
import io.plaidapp.core.designernews.data.poststory.model.NewStoryRequest;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.data.votes.model.UpvoteCommentRequest;
import io.plaidapp.core.designernews.data.votes.model.UpvoteStoryRequest;
import io.plaidapp.core.designernews.domain.model.Comment;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import retrofit2.Call;
import retrofit2.http.*;

public interface DesignerNewsService {
  @EnvelopePayload("stories")
  @GET("api/v2/stories")
  Call<List<Story>> getTopStoriesV2(@Query("page") Integer page);

  @EnvelopePayload("stories")
  @GET("api/v2/stories/recent")
  Call<List<Story>> getRecentStoriesV2(@Query("page") Integer page);

  @EnvelopePayload("stories")
  @GET("api/v1/stories")
  Call<List<Story>> getTopStories(@Query("page") Integer page);

  @EnvelopePayload("stories")
  @GET("api/v1/stories/recent")
  Call<List<Story>> getRecentStories(@Query("page") Integer page);

  @EnvelopePayload("stories")
  @GET("api/v1/stories/search")
  Call<List<Story>> search(
    @Query("query") String query,
    @Query("page") Integer page
  );

  @EnvelopePayload("users")
  @GET("api/v2/users/{ids}")
  CompletableFuture<retrofit2.Response<List<User>>> getUsers(
    @Path("ids") String userids
  );

  @EnvelopePayload("users")
  @GET("api/v2/me")
  CompletableFuture<retrofit2.Response<List<User>>> getAuthedUser();

  @FormUrlEncoded
  @POST("oauth/token")
  CompletableFuture<retrofit2.Response<AccessToken>> login(
    @FieldMap Map<String, String> loginParams
  );

  @EnvelopePayload("story")
  @POST("api/v2/stories/{id}/upvote")
  Call<Story> upvoteStory(@Path("id") Long storyId);

  @Headers("Content-Type: application/vnd.api+json")
  @POST("api/v2/upvotes")
  CompletableFuture<retrofit2.Response<Void>> upvoteStoryV2(
    @Body UpvoteStoryRequest request
  );

  @EnvelopePayload("stories")
  @Headers("Content-Type: application/vnd.api+json")
  @POST("api/v2/stories")
  Call<List<Story>> postStory(@Body NewStoryRequest story);

  @EnvelopePayload("comments")
  @GET("api/v2/comments/{ids}")
  CompletableFuture<retrofit2.Response<List<CommentResponse>>> getComments(
    @Path("ids") String commentIds
  );

  @FormUrlEncoded
  @POST("api/v1/stories/{id}/reply")
  Call<Comment> comment(
    @Path("id") Long storyId,
    @Field("comment[body]") String comment
  );

  @FormUrlEncoded
  @POST("api/v1/comments/{id}/reply")
  Call<Comment> replyToComment(
    @Path("id") Long commentId,
    @Field("comment[body]") String comment
  );

  @Headers("Content-Type: application/vnd.api+json")
  @POST("api/v2/comment_upvotes")
  CompletableFuture<retrofit2.Response<Void>> upvoteComment(
    @Body UpvoteCommentRequest request
  );

  public static final String ENDPOINT = "https:";
}
