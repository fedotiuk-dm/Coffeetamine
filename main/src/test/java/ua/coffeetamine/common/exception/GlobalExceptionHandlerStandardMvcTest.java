package ua.coffeetamine.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerStandardMvcTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void methodNotSupportedMapsTo405() {
    ProblemDetail pd = handler.handleStandardMvc(new HttpRequestMethodNotSupportedException("GET"));
    assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), pd.getStatus());
    assertEquals("VAL_005", pd.getProperties().get("errorCode"));
  }

  @Test
  void unsupportedMediaTypeMapsTo415() {
    ProblemDetail pd =
        handler.handleStandardMvc(new HttpMediaTypeNotSupportedException("text/plain"));
    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), pd.getStatus());
    assertEquals("VAL_005", pd.getProperties().get("errorCode"));
  }
}
