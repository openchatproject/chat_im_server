package com.openchat.secureim.mappers;

import com.openchat.secureim.websocket.InvalidWebsocketAddressException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class InvalidWebsocketAddressExceptionMapper implements ExceptionMapper<InvalidWebsocketAddressException> {
  @Override
  public Response toResponse(InvalidWebsocketAddressException exception) {
    return Response.status(Response.Status.BAD_REQUEST).build();
  }
}
