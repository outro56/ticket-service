package cmuoh.ticketService;

import cmuoh.ticketService.exception.mapper.InvalidSeatHoldRequestExceptionMapper;
import cmuoh.ticketService.exception.mapper.NoSeatsAvailableExceptionMapper;
import cmuoh.ticketService.exception.mapper.ReservationNotFoundExceptionMapper;
import cmuoh.ticketService.exception.mapper.VenueLevelNotFoundExceptionMapper;
import cmuoh.ticketService.healthCheck.OkHealthCheck;
import cmuoh.ticketService.resource.VenueTicketManager;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

/**
 * Ticket service application
 */
public class TicketService extends Application<TicketServiceConfiguration> {

    public static void main(String[] args) throws Exception {
        new TicketService().run(args);
    }

    @Override
    public void initialize(Bootstrap bootstrap) {
        super.initialize(bootstrap);
    }

    @Override
    public String getName() {
        return "ticket-service";
    }

    @Override
    public void run(TicketServiceConfiguration configuration, Environment environment) throws Exception {
        environment.getObjectMapper().registerModule(new JavaTimeModule());
        environment.getObjectMapper().setDateFormat(new ISO8601DateFormat());

        // health check, can be accessed on the admin port
        environment.healthChecks().register("ok", new OkHealthCheck());

        // convert our exceptions to status codes for the user
        environment.jersey().register(new InvalidSeatHoldRequestExceptionMapper());
        environment.jersey().register(new NoSeatsAvailableExceptionMapper());
        environment.jersey().register(new VenueLevelNotFoundExceptionMapper());
        environment.jersey().register(new ReservationNotFoundExceptionMapper());

        // register the venue ticket manager resource
        environment.jersey().register(new VenueTicketManager(configuration.getVenueSeatingConfiguration()));
    }
}