package com.igot.cb.pores.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CustomExceptionTest {

  @Test
  void constructor_setsCodeMessageAndHttpStatus() {
    CustomException exception = new CustomException("ERROR", "Something went wrong", HttpStatus.BAD_REQUEST);

    assertEquals("ERROR", exception.getCode());
    assertEquals("Something went wrong", exception.getMessage());
    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatusCode());
  }

  @Test
  void noArgsConstructor_leavesFieldsNull() {
    CustomException exception = new CustomException();

    assertNull(exception.getCode());
    assertNull(exception.getMessage());
    assertNull(exception.getHttpStatusCode());
  }

  @Test
  void setters_updateFieldsIndependently() {
    CustomException exception = new CustomException();

    exception.setCode("NOT_FOUND");
    exception.setMessage("Community not found");
    exception.setHttpStatusCode(HttpStatus.NOT_FOUND);

    assertEquals("NOT_FOUND", exception.getCode());
    assertEquals("Community not found", exception.getMessage());
    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatusCode());
  }
}
