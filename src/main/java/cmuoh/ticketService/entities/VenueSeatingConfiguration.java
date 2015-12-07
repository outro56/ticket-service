package cmuoh.ticketService.entities;

import cmuoh.ticketService.util.HoldTimeStampProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * Created by chibuike on 12/6/15.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VenueSeatingConfiguration {
    @JsonProperty(required = true)
    @NotEmpty
    @Valid
    private List<SeatLevel> seatLevels;

    @JsonProperty
    @Min(1)
    private Integer holdsExpireAfter = HoldTimeStampProvider.DEFAULT_TTL_SECONDS;

    /**
     * The seating levels in the venue.
     *
     */
    public List<SeatLevel> getSeatLevels() {
        return seatLevels;
    }

    public void setSeatLevels(List<SeatLevel> seatLevels) {
        this.seatLevels = seatLevels;
    }

    /**
     * Number of seconds to hold an unconfirmed reservation
     */
    public Integer getHoldsExpireAfter() {
        return holdsExpireAfter;
    }

    public void setHoldsExpireAfter(Integer holdsExpireAfter) {
        this.holdsExpireAfter = holdsExpireAfter;
    }
}
