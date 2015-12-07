package cmuoh.ticketService.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import java.util.Collection;
import java.util.List;

/**
 * Details about reservation holds on a seating level
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatLevelHoldDetail {
    @Deprecated
    public SeatLevelHoldDetail() {

    }

    public SeatLevelHoldDetail(String name, Integer rows, Integer seatsInRow, Collection<SeatHold> seatHolds) {
        this.name = name;
        this.rows = rows;
        this.seatsInRow = seatsInRow;
        this.seatHolds = seatHolds;
    }

    @JsonProperty(required = true)
    @NotEmpty
    private String name;

    @JsonProperty(required = true)
    @Min(1)
    private Integer rows;

    @JsonProperty(required = true)
    @Min(1)
    private Integer seatsInRow;

    @JsonProperty(required = true)
    @NotEmpty
    private Collection<SeatHold> seatHolds;

    @JsonProperty
    public Integer getAvailableSeats() {
        if (seatHolds == null) {
            return null;
        }

        int capacity = rows * seatsInRow;
        int numOfSeatHolds = seatHolds.stream().map(SeatHold::getSeatCount).reduce(0, (i, count) -> i + count);

        return capacity - numOfSeatHolds;
    }

    @JsonProperty
    public Long getConfirmedHolds() {
        if (seatHolds == null) {
            return null;
        }

        return seatHolds.stream().filter(SeatHold::getConfirmed).count();
    }

    public Collection<SeatHold> getSeatHolds() {
        return seatHolds;
    }

    public void setSeatHolds(List<SeatHold> seatHolds) {
        this.seatHolds = seatHolds;
    }

    public Integer getRows() {
        return rows;
    }

    public void setRows(Integer rows) {
        this.rows = rows;
    }

    public Integer getSeatsInRow() {
        return seatsInRow;
    }

    public void setSeatsInRow(Integer seatsInRow) {
        this.seatsInRow = seatsInRow;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
