package org.example.wastebag.resource;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.example.wastebag.model.Owner;

import java.util.List;

@Path("/api/owners")
public class OwnerResource {

    public record OwnerIn(String name, String unit, String phone) {}

    @GET
    public List<Owner> list() {
        return Owner.listAll();
    }

    @POST
    @Transactional
    public Owner create(OwnerIn in) {
        if (in == null || in.name() == null || in.name().isBlank()
                || in.unit() == null || in.unit().isBlank()) {
            throw new WebApplicationException("姓名和房号必填", Response.Status.BAD_REQUEST);
        }
        if (Owner.find("unit", in.unit()).firstResult() != null) {
            throw new WebApplicationException("房号已存在: " + in.unit(), Response.Status.CONFLICT);
        }
        Owner o = new Owner();
        o.name = in.name().trim();
        o.unit = in.unit().trim();
        o.phone = in.phone();
        o.persist();
        return o;
    }
}
