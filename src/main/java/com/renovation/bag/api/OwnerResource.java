package com.renovation.bag.api;

import com.renovation.bag.domain.Owner;
import com.renovation.bag.repo.OwnerRepository;
import com.renovation.bag.support.BusinessException;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/owners")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OwnerResource {

    @Inject OwnerRepository owners;

    @GET
    public List<Owner> list() {
        return owners.listAll();
    }

    @POST
    @Transactional
    public Owner create(Owner owner) {
        if (owner.getCode() == null || owner.getName() == null || owner.getRoom() == null) {
            throw new BusinessException("code/name/room 必填");
        }
        if (owners.findByCode(owner.getCode()) != null) {
            throw new BusinessException("业主编号已存在: " + owner.getCode());
        }
        owners.persist(owner);
        return owner;
    }
}
