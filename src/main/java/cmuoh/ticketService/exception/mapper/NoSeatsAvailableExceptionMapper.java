package cmuoh.ticketService.exception.mapper;

import cmuoh.ticketService.exception.NoSeatsAvailableException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Created by cmuoh on 12/7/15.
 */
public class NoSeatsAvailableExceptionMapper implements ExceptionMapper<NoSeatsAvailableException> {
    @Override
    public Response toResponse(NoSeatsAvailableException e) {
        return Response.noContent().build();
    }
}
