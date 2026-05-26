package com.geo.analytics.integration.flyway;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("rls-it")
public class ApiWorkerLoginFlywayCallback implements Callback {

    public static final String API_WORKER_PASSWORD = "test";

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try (Statement st = context.getConnection().createStatement()) {
            st.execute("ALTER ROLE api_worker WITH LOGIN PASSWORD '" + API_WORKER_PASSWORD + "'");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getCallbackName() {
        return "ApiWorkerLogin";
    }
}
