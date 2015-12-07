package cmuoh.ticketService.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by chibuike on 12/7/15.
 */
public class CustomMessage {
    @JsonProperty
    private String message;

    public CustomMessage() {

    }

    public CustomMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
