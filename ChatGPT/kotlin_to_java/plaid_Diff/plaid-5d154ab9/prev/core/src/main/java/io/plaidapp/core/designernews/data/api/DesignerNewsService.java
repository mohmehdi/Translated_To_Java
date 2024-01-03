package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.data.api.EnvelopePayload;
import io.plaidapp.core.designernews.data.api.model.AccessToken;
import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.designernews.data.api.model.NewStoryRequest;
import io.plaidapp.core.designernews.data.api.model.Story;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.data.votes.model.UpvoteCommentRequest;
import io.plaidapp.core.designernews.data.votes.model.UpvoteStoryRequest;
import kotlinx.coroutines.experimental.Deferred;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

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
    Call<List<Story>> search(@Query("query") String query, @Query("page") Integer page);

    @EnvelopePayload("users")
    @GET("api/v2/users/{ids}")
    Deferred<Response<List<User>>> getUsers(@Path("ids") String userids);

    @EnvelopePayload("users")
    @GET("api/v2/me")
    Deferred<Response<List<User>>> getAuthedUser();

    @FormUrlEncoded
    @POST("oauth/token")
    Deferred<Response<AccessToken>> login(@FieldMap Map<String, String> loginParams);

    @EnvelopePayload("story")
    @POST("api/v2/stories/{id}/upvote")
    Call<Story> upvoteStory(@Path("id") Long storyId);

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/upvotes")
    Deferred<Response<Unit>> upvoteStoryV2(@Body UpvoteStoryRequest request);

    @EnvelopePayload("stories")
    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/stories")
    Call<List<Story>> postStory(@Body NewStoryRequest story);

    @EnvelopePayload("comments")
    @GET("api/v2/comments/{ids}")
    Deferred<Response<List<CommentResponse>>> getComments(@Path("ids") String commentIds);

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
    Deferred<Response<Unit>> upvoteComment(@Body UpvoteCommentRequest request);

    interface Companion {
        String ENDPOINT = "https:
    }
}