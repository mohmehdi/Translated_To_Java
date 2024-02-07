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

    @EnvelopePayload(value = "stories", key = "type")
    @GET("api/v2/stories")
    Call<List<StoryResponse>> getStories(@Query("page") Integer page);

    @EnvelopePayload(value = "stories", key = "type")
    @GET("api/v2/stories/{ids}")
    Call<List<StoryResponse>> getStories(@Path("ids") String commaSeparatedIds);

    @EnvelopePayload(value = "users", key = "type")
    @GET("api/v2/users/{ids}")
    Call<List<User>> getUsers(@Path("ids") String userids);

    @EnvelopePayload(value = "users", key = "type")
    @GET("api/v2/me")
    Call<List<LoggedInUserResponse>> getAuthedUser();

    @FormUrlEncoded
    @POST("oauth/token")
    Call<AccessToken> login(@FieldMap Map<String, String> loginParams);

    @GET("search?t=story")
    Call<List<ResponseBody>> search(
            @Query("q") String query,
            @Query("p") Integer page);

    @EnvelopePayload(value = "story", key = "type")
    @POST("api/v2/stories/{id}/upvote")
    Call<Story> upvoteStory(@Path("id") Long storyId);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/upvotes")
    Call<ResponseBody> upvoteStoryV2(@Body RequestBody request);

    @EnvelopePayload(value = "stories", key = "type")
    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/stories")
    Call<List<StoryResponse>> postStory(@Body RequestBody story);

    @EnvelopePayload(value = "comments", key = "type")
    @GET("api/v2/comments/{ids}")
    Call<List<CommentResponse>> getComments(@Path("ids") String commentIds);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/comments")
    Call<PostCommentResponse> comment(@Body RequestBody comment);

    @FormUrlEncoded
    @POST("api/v1/comments/{id}/reply")
    Call<Comment> replyToComment(
            @Path("id") Long commentId,
            @Field("comment[body]") String comment);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/comment_upvotes")
    Call<ResponseBody> upvoteComment(@Body RequestBody request);

    companion object {
        public static final String ENDPOINT = "https:";
    }
}