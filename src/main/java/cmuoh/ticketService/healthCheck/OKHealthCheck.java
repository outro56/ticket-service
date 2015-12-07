package cmuoh.ticketService.healthCheck;

import com.codahale.metrics.health.HealthCheck;

/**
 * Created by cmuoh on 12/7/15.
 */
public class OkHealthCheck extends HealthCheck {

    public OkHealthCheck() {
    }

    @Override
    protected Result check() throws Exception {
        return Result.healthy();
    }
}
