package com.renovation.bag.api;

import com.renovation.bag.domain.ScanLog;
import com.renovation.bag.service.ScanLogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/scans")
@Produces(MediaType.APPLICATION_JSON)
public class ScanLogResource {

    @Inject ScanLogService scanLogs;

    @GET
    @Path("/recent")
    public List<ScanLog> recent(@QueryParam("limit") @DefaultValue("20") int limit) {
        return scanLogs.recent(limit);
    }
}
