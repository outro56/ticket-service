package cmuoh.ticketService.exception.mapper;

import cmuoh.ticketService.entities.CustomMessage;
import cmuoh.ticketService.exception.VenueLevelNotFoundException;
import org.eclipse.jetty.http.HttpStatus;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Created by cmuoh on 12/7/15.
 */
public class VenueLevelNotFoundExceptionMapper implements ExceptionMapper<VenueLevelNotFoundException> {
    @Override
    public Response toResponse(VenueLevelNotFoundException e) {
        return Response.status(HttpStatus.NOT_FOUND_404)
                .entity(new CustomMessage(e.getMessage()))
                .build();
    }
}
