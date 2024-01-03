package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.data.api.EnvelopePayload;
import io.plaidapp.core.designernews.data.login.model.AccessToken;
import io.plaidapp.core.designernews.data.login.model.LoggedInUserResponse;
import io.plaidapp.core.designernews.data.poststory.model.NewStoryRequest;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.data.votes.model.UpvoteCommentRequest;
import io.plaidapp.core.designernews.data.votes.model.UpvoteStoryRequest;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;
import java.util.Map;

public interface DesignerNewsService {

    @EnvelopePayload("stories")
    @GET("api/v2/stories")
    Deferred<Response<List<StoryResponse>>> getStories(@Query("page") Integer page);

    @EnvelopePayload("stories")
    @GET("api/v2/stories/{ids}")
    Deferred<Response<List<StoryResponse>>> getStories(@Path("ids") String commaSeparatedIds);

    @EnvelopePayload("users")
    @GET("api/v2/users/{ids}")
    Deferred<Response<List<User>>> getUsers(@Path("ids") String userids);

    @EnvelopePayload("users")
    @GET("api/v2/me")
    Deferred<Response<List<LoggedInUserResponse>>> getAuthedUser();

    @FormUrlEncoded
    @POST("oauth/token")
    Deferred<Response<AccessToken>> login(@FieldMap Map<String, String> loginParams);

    
    @GET("search?t=story")
    Deferred<Response<List<String>>> search(
        @Query("q") String query,
        @Query("p") Integer page
    );

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

    @Headers("Content-Type: application/vnd.api+json")
    @POST("api/v2/comment_upvotes")
    Deferred<Response<Unit>> upvoteComment(@Body UpvoteCommentRequest request);

    interface Companion {
        String ENDPOINT = "https:
    }
}