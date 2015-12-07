# ticket-service
A simple ticket service that facilitates the discovery, temporary hold, and final reservation of seats within a high-demand performance venue

## Overview

This service was developed as a web service using the dropwizard framework. All data is held in memory, server restarts erases all hold information

The configuration for the service is stored in _ticket-service.yaml_. That is also where the seat level configuration for the venue is stored. 

The following assumptions are being made when configuring the seat levels in the venues

- The ordinal position of each seat level in the array is assumed to be their ID
- Seat levels with high ID values are considered to be better than seat levels with low ID values
- Within each seating level, all seats have equal preference
- Seating preference is not taken into account when holds are requested, thus any available seat is reserved
- Hold requests do not span seat levels

### Main classes 

* `ReservationManager` provides an abstraction for dealing with reservation holds at a seating level
* `ReservationHold` details about a reservation hold on a resource
* `cmuoh.ticketService.entities.*` classes under this package are mainly for representing state/configuraton. 
They are mainly used to communicating with clients of this library
* `VenueTicketManager` implements the API access and REST interface for interacting with the venue service
* `TicketServiceConfiguration` contains configuration for running the application and configuring the seating levels at the venue. 
* `ticket-service.yaml` hydrates the initial values for `TicketServiceConfiguration` when the application is run as a server

### Notes on hold expiration

Unconfirmed holds are set to expire lazily as needed and the cost is amortized over each request to the service. 

## Building the service

* To build and package the jars for the application example run (NOTE: this would also run the unit tests)

        mvn clean package

* To run the server run.

         java -jar target/ticket-service-1.0-SNAPSHOT.jar server ticket-service.yaml 

## Interacting with the application.

* Return the number of available seats in the venue (venueLevel parameter is optional)

        curl -X GET http://localhost:8080/venue/numSeatsAvailable?venueLevel={venueLevel}

* Find and hold available seats in the venue (the minLevel, maxLevel parameters are optional)

        curl -X POST -d {email-address} http://localhost:8080/venue/findAndHoldSeats?numSeats={numSeats}&minLevel={minLevel}&maxLevel={minLevel}

* Reserves a held seat

        curl -X PUT -d {email-address} http://localhost:8080/venue/reserveSeats?seatHoldId={seatHoldId}

* Get a dump of hold details in the venue (venueLevel parameter is optional)

        curl -X GET http://localhost:8080/venue/holdDetails?venueLevel={venueLevel}

