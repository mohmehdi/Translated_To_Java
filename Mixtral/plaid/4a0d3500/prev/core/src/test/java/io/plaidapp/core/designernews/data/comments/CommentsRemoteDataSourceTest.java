public class CommentsRemoteDataSourceTest {

  private String body = "Plaid is awesome";

  private DesignerNewsService service = mock(DesignerNewsService.class);
  private CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(
    service
  );

  @Test
  public void getComments_whenRequestSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success(repliesResponses));

    whenever(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success(repliesResponses), response);
  }

  @Test
  public void getComments_forMultipleComments()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success(repliesResponses));

    whenever(service.getComments("11,12")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(11L, 12L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success(repliesResponses), response);
  }

  @Test
  public void getComments_whenRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.error(400, errorResponseBody));

    whenever(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertTrue(response instanceof Result.Error);
  }

  @Test
  public void getComments_whenResponseEmpty()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success((List<CommentResponse>) null));

    whenever(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertTrue(response instanceof Result.Error);
  }

  @Test
  void getComments_whenException() throws UnknownHostException {
    // Given that the service throws an exception
    when(service.getComments("1"))
      .thenAnswer(invocation -> {
        throw new UnknownHostException();
      });

    // When getting the list of comments
    Result<List<Comment>> response = dataSource.getComments(List.of(1L));

    // Then the response is not successful
    assertTrue(response instanceof Result.Error);
  }

  @Test(expected = IllegalStateException.class)
  public void comment_whenParentCommentIdAndStoryIdNull()
    throws ExecutionException, InterruptedException {
    dataSource.comment("text", null, null, 11L);
  }

  @Test
  @Test
  public void comment_whenException() {
    // Given that the service throws an exception
    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
    when(service.comment(request))
      .thenAnswer(invocation -> {
        throw new UnknownHostException();
      });

    // When adding a comment
    Result response = dataSource.comment(body, 11L, null, 111L);

    // Then the response is not successful
    assertTrue(response instanceof Result.Error);
  }

  @Test
  public void comment_withNoComments()
    throws ExecutionException, InterruptedException {
    Response<PostCommentResponse> response = Response.success(
      new PostCommentResponse(null)
    );

    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");

    whenever(service.comment(request)).thenReturn(result);

    Result<CommentResponse> result = dataSource
      .comment(body, 11L, null, 111L)
      .get();

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void comment_withComments()
    throws ExecutionException, InterruptedException {
    Response<PostCommentResponse> response = Response.success(
      new PostCommentResponse(Arrays.asList(replyResponse1))
    );

    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");

    whenever(service.comment(request)).thenReturn(result);

    Result<CommentResponse> result = dataSource
      .comment(body, 11L, null, 111L)
      .get();

    Assert.assertEquals(result, Result.Success(replyResponse1));
  }
}
