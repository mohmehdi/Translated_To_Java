
package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.data.api.EnvelopePayload;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.designernews.data.comments.model.PostCommentResponse;
import io.plaidapp.core.designernews.data.login.model.AccessToken;
import io.plaidapp.core.designernews.data.login.model.LoggedInUserResponse;
import io.plaidapp.core.designernews.data.poststory.model.NewStoryRequest;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface DesignerNewsService {

  @EnvelopePayload("stories")
  @GET("api/v2/stories")
  Call < Response < List < StoryResponse >>> getStories(@Query("page") Integer page);

  @EnvelopePayload("stories")
  @GET("api/v2/stories/{ids}")
  Call < Response < List < StoryResponse >>> getStories(@Path("ids") String commaSeparatedIds);

  @EnvelopePayload("users")
  @GET("api/v2/users/{ids}")
  Call < Response < List < User >>> getUsers(@Path("ids") String userids);

  @EnvelopePayload("users")
  @GET("api/v2/me")
  Call < Response < List < LoggedInUserResponse >>> getAuthedUser();

  @FormUrlEncoded
  @POST("oauth/token")
  Call < Response < AccessToken >> login(@FieldMap Map < String, String > loginParams);

  @GET("search?t=story")
  Call < Response < List < String >>> search(
    @Query("q") String query,
    @Query("p") Integer page);

  @EnvelopePayload("story")
  @POST("api/v2/stories/{id}/upvote")
  Call < Story > upvoteStory(@Path("id") Long storyId);

  @Headers("Content-Type: application/vnd.api+json")
  @POST("api/v2/upvotes")
  Call < Response < Void >> upvoteStoryV2(@Body RequestBody request);

  @POST("api/v2/stories")
  @Headers("Content-Type: application/vnd.api+json")
  Call < List < Story >> postStory(@Body NewStoryRequest story);

  @EnvelopePayload("comments")
  @GET("api/v2/comments/{ids}")
  Call < Response < List < CommentResponse >>> getComments(@Path("ids") String commentIds);

  @POST("api/v2/comments")
  @Headers("Content-Type: application/vnd.api+json")
  Call < PostCommentResponse > comment(@Body NewCommentRequest comment);

  @FormUrlEncoded
  @POST("api/v1/comments/{id}/reply")
  Call < Comment > replyToComment(
    @Path("id") Long commentId,
    @Field("comment[body]") String comment);

  @Headers("Content-Type: application/vnd.api+json")
  @POST("api/v2/comment_upvotes")
  Call < Response < Void >> upvoteComment(@Body RequestBody request);

    String ENDPOINT = "https:";
}