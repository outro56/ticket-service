package cmuoh.ticketService.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Seat level details for a venue
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatLevel {
    @JsonProperty(required = true)
    @NotEmpty
    private String name;

    @JsonProperty(required = true)
    @Min(0)
    private BigDecimal price;

    @Min(1)
    @JsonProperty(required = true)
    private int rows;

    @Min(1)
    @JsonProperty(required = true)
    private int seatsInRow;

    /**
     * Name for the seating level
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Cost for each seat in this level
     */
    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * Number of rows for this level
     */
    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    /**
     * Number of seats in each row
     */
    public int getSeatsInRow() {
        return seatsInRow;
    }

    public void setSeatsInRow(int seatsInRow) {
        this.seatsInRow = seatsInRow;
    }
}
