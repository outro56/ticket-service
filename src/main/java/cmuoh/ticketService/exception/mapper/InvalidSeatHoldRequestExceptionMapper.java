package cmuoh.ticketService.exception.mapper;

import cmuoh.ticketService.entities.CustomMessage;
import cmuoh.ticketService.exception.InvalidSeatHoldRequestException;
import org.eclipse.jetty.http.HttpStatus;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Created by cmuoh on 12/7/15.
 */
public class InvalidSeatHoldRequestExceptionMapper implements ExceptionMapper<InvalidSeatHoldRequestException> {
    @Override
    public Response toResponse(InvalidSeatHoldRequestException e) {
        return Response.status(HttpStatus.BAD_REQUEST_400)
                .entity(new CustomMessage(e.getMessage()))
                .build();
    }
}
