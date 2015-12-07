package cmuoh.ticketService;

import cmuoh.ticketService.entities.VenueSeatingConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by cmuoh on 12/7/15.
 */
public class TicketServiceConfiguration extends Configuration{

    @JsonProperty(value = "venueSeating", required = true)
    @NotNull
    @Valid
    private VenueSeatingConfiguration venueSeatingConfiguration;

    public VenueSeatingConfiguration getVenueSeatingConfiguration() {
        return venueSeatingConfiguration;
    }
}
