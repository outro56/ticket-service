package cmuoh.ticketService.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Email;

import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * Represents resource holds for a venue seats
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SeatHold {

    @JsonProperty
    private Integer reservationId;

    @JsonProperty
    @Min(0)
    private Integer level;

    @JsonProperty
    @Min(0)
    private Integer seatCount;

    @JsonProperty
    private OffsetDateTime timeStamp;

    @Deprecated
    public SeatHold() {

    }

    public SeatHold(Integer reservationId, Integer level, String customerEmail, Integer seatCount,
                    Boolean confirmed, OffsetDateTime timeStamp) {
        this.reservationId = reservationId;
        this.level = level;
        this.seatCount = seatCount;
        this.customerEmail = customerEmail;
        this.confirmed = confirmed;
        this.timeStamp = timeStamp;
    }

    @JsonProperty
    @Email
    private String customerEmail;

    @JsonProperty
    private Boolean confirmed;

    public Integer getReservationId() {
        return reservationId;
    }

    public void setReservationId(Integer reservationId) {
        this.reservationId = reservationId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer row) {
        this.seatCount = seatCount;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public OffsetDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(OffsetDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }
}
