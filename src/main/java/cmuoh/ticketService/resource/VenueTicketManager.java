package cmuoh.ticketService.resource;

import cmuoh.ticketService.dataAccess.ReservationHold;
import cmuoh.ticketService.dataAccess.ReservationManager;
import cmuoh.ticketService.entities.SeatHold;
import cmuoh.ticketService.entities.SeatLevel;
import cmuoh.ticketService.entities.SeatLevelHoldDetail;
import cmuoh.ticketService.entities.VenueSeatingConfiguration;
import cmuoh.ticketService.exception.InvalidSeatHoldRequestException;
import cmuoh.ticketService.exception.NoSeatsAvailableException;
import cmuoh.ticketService.exception.ReservationNotFoundException;
import cmuoh.ticketService.exception.VenueLevelNotFoundException;
import cmuoh.ticketService.util.HoldTimeStampProvider;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages the
 */
@Path("/venue")
@Produces(MediaType.APPLICATION_JSON)
public class VenueTicketManager {
    private static final Logger LOG = LoggerFactory.getLogger(VenueTicketManager.class);

    final VenueSeatingConfiguration configuration;
    final AtomicInteger reservationIdCounter = new AtomicInteger(1000000329);
    final ReservationManager[] seatLevelResourceManager;
    final ConcurrentHashMap<Integer, Integer> holdId2SeatLevel = new ConcurrentHashMap<>();
    final EmailValidator emailValidator = new EmailValidator();

    /**
     * Initialize the venue seat reservation manager
     *
     * @param configuration
     * @throws Exception
     */
    public VenueTicketManager(VenueSeatingConfiguration configuration) throws Exception {
        if (configuration.getSeatLevels().size() == 0) {
            throw new IllegalArgumentException("configuration.seatLevels");
        }

        this.configuration = configuration;
        this.seatLevelResourceManager = new ReservationManager[configuration.getSeatLevels().size()];

        for (int lvlNum = 0; lvlNum < configuration.getSeatLevels().size(); ++lvlNum) {
            SeatLevel lvl = configuration.getSeatLevels().get(lvlNum);
            if (lvl.getRows() < 1) {
                throw new Exception(String.format("Invalid number of rows (%d) for seating level %d", lvl.getRows(),
                        lvlNum));
            }

            if (lvl.getSeatsInRow() < 1) {
                throw new Exception(String.format("Invalid number of seats (%d) for seating level %d, row %d",
                        lvlNum, lvl.getRows(), lvl.getSeatsInRow()));
            }

            int capacity = lvl.getRows() * lvl.getSeatsInRow();

            ReservationManager resource = new ReservationManager(lvlNum, capacity,
                    new HoldTimeStampProvider(configuration.getHoldsExpireAfter()),
                    lvl.getName());

            resource.setReservationIdCounter(reservationIdCounter);

            this.seatLevelResourceManager[lvlNum] = resource;
        }
    }

    /**
     * The details for reservation holds at the venue
     *
     * @return
     * @throws VenueLevelNotFoundException
     */
    @GET
    @Timed
    @ExceptionMetered
    @Path("/holdDetails")
    public List<SeatLevelHoldDetail> venueHoldDetails(
            @QueryParam("venueLevel") Integer venueLevel
    ) throws VenueLevelNotFoundException {
        ArrayList<SeatLevelHoldDetail> seatLevelHoldDetails = new ArrayList<>();

        int fromLevel = 0;
        int toLevel = seatLevelResourceManager.length - 1;

        if (venueLevel != null) {
            if (venueLevel < 0 || venueLevel >= seatLevelResourceManager.length) {
                throw new VenueLevelNotFoundException(venueLevel);
            }

            fromLevel = venueLevel;
            toLevel = venueLevel;
        }

        for (; fromLevel <= toLevel; ++fromLevel) {
            ReservationManager resource = seatLevelResourceManager[fromLevel];

            SeatLevel seatLevel = configuration.getSeatLevels().get(fromLevel);

            List<SeatHold> seatHolds = resource.getAllReservations().stream()
                    .map(this::buildSeatHold)
                    .collect(Collectors.toList());

            seatLevelHoldDetails.add(new SeatLevelHoldDetail(seatLevel.getName(), seatLevel.getRows(),
                    seatLevel.getSeatsInRow(), seatHolds));
        }

        return seatLevelHoldDetails;
    }

    /**
     * The number of seats in the requested level that are neither held nor reserved
     *
     * @param venueLevel a numeric venue level identifier to limit the search
     * @return the number of tickets available on the provided level
     * @throws VenueLevelNotFoundException
     */
    @GET
    @Timed
    @ExceptionMetered
    @Path("/numSeatsAvailable")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public int numSeatsAvailable(
            @QueryParam("venueLevel") Integer venueLevel
    ) throws VenueLevelNotFoundException {
        int fromLevel = 0;
        int toLevel = seatLevelResourceManager.length - 1;

        if (venueLevel != null) {
            if (venueLevel < 0 || venueLevel >= seatLevelResourceManager.length) {
                throw new VenueLevelNotFoundException(venueLevel);
            }

            fromLevel = venueLevel;
            toLevel = venueLevel;
        }

        int available = 0;
        for (; fromLevel <= toLevel; ++fromLevel) {
            ReservationManager resource = seatLevelResourceManager[fromLevel];
            available += resource.countAvailableSlots();
        }

        return available;
    }

    /**
     * Find and hold the best available seats for a customer
     *
     * @param numSeats the number of seats to find and hold
     * @param minLevel the minimum venue level
     * @param maxLevel the maximum venue level
     * @param customerEmail unique identifier for the customer
     * @return a SeatHold object identifying the specific seats and related information
     * @throws InvalidSeatHoldRequestException, NoSeatsAvailableException
     */
    @POST
    @Timed
    @ExceptionMetered
    @Path("/findAndHoldSeats")
    public SeatHold findAndHoldSeats(
            @QueryParam("numSeats") @NotNull Integer numSeats,
            @QueryParam("minLevel") Integer minLevel,
            @QueryParam("maxLevel") Integer maxLevel,
            @Email @NotNull @NotBlank String customerEmail
    ) throws InvalidSeatHoldRequestException, NoSeatsAvailableException {
        if (numSeats == 0) {
            return null;
        }

        if (numSeats < 0) {
            throw new InvalidSeatHoldRequestException(
                    String.format("Invalid number of hold [%d] being requested", numSeats));
        }

        int lo = minLevel != null ? minLevel : 0;
        int hi = maxLevel != null ? maxLevel : seatLevelResourceManager.length - 1;

        if (lo > hi || lo < 0 || hi >= seatLevelResourceManager.length) {
            throw new InvalidSeatHoldRequestException(
                    String.format("Seat hold level constraints are not valid [%d, %d]", lo, hi));
        }

        validateCustomerEmail(customerEmail);

        for (; hi >= lo; --hi) {
            ReservationManager resource = seatLevelResourceManager[hi];
            ReservationHold hold = resource.requestHold(numSeats, customerEmail);

            if (hold != null) {
                SeatHold seatHold = buildSeatHold(hold);
                holdId2SeatLevel.put(seatHold.getReservationId(), seatHold.getLevel());

                return seatHold;
            }
        }

        throw new NoSeatsAvailableException();
    }

    private void validateCustomerEmail(String customerEmail) throws InvalidSeatHoldRequestException {
        if (customerEmail == null || customerEmail.isEmpty()) {
            throw new InvalidSeatHoldRequestException("Customer email cannot be null or empty");
        }

        if (!emailValidator.isValid(customerEmail, null)){
            throw new InvalidSeatHoldRequestException(String.format("%s is not a valid email address", customerEmail));
        }
    }

    private SeatHold buildSeatHold(ReservationHold hold) {
        return new SeatHold(hold.getReservationId(), hold.getResourceId(),
                hold.getData(), hold.getSlots().size(), hold.isConfirmed(), hold.getTimeStamp());
    }

    /**
     * Commit seats held for a specific customer
     *
     * @param seatHoldId the seat hold identifier
     * @param customerEmail the email address of the customer to which the seat hold is assigned
     * @return a reservation confirmation code
     */
    @PUT
    @Timed
    @ExceptionMetered
    @Path("/reserveSeats")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String reserveSeats(
            @QueryParam("seatHoldId") @NotNull Integer seatHoldId,
            @Email @NotBlank String customerEmail
    ) throws InvalidSeatHoldRequestException, ReservationNotFoundException {
        validateCustomerEmail(customerEmail);

        Integer seatLevelId = holdId2SeatLevel.get(seatHoldId);
        if (seatLevelId == null) {
            throw new InvalidSeatHoldRequestException(
                    String.format("Seat hold #%d does not exist in the venue", seatHoldId));
        }

        ReservationManager resource = seatLevelResourceManager[seatLevelId];
        ReservationHold hold = resource.getReservation(seatHoldId);
        if (hold == null) {
            throw new ReservationNotFoundException(seatHoldId, customerEmail);
        }

        SeatHold seatHold = buildSeatHold(hold);
        if (!Objects.equals(seatHold.getCustomerEmail(), customerEmail)) {
            throw new InvalidSeatHoldRequestException(
                    String.format("Seat hold #%d is not associated with customer email %s",
                            seatHoldId, customerEmail));
        }

        if (!resource.confirmHold(seatHold.getReservationId())) {
            throw new ReservationNotFoundException(seatHoldId, customerEmail);
        }

        return generateReservationCode(seatHoldId, customerEmail);
    }

    private static String generateReservationCode(int seatHoldId, String customerEmail) {
        return Integer.toString(seatHoldId, 16);
    }
}

