package com.renovation.bag.api;

import com.renovation.bag.api.dto.*;
import com.renovation.bag.service.BagService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/bags")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BagResource {

    @Inject BagService bagService;

    /** 物业登记发袋 */
    @POST
    @Path("/issue")
    public List<BagDto> issue(IssueRequest req) {
        return bagService.issue(req.ownerCode, req.bagCodes, req.count, req.prefix);
    }

    /** 未使用袋子退回（不计费） */
    @POST
    @Path("/return")
    public List<BagDto> returnBags(ReturnRequest req) {
        return bagService.returnBags(req.bagCodes);
    }

    /** 清运员扫码（幂等：重复扫码只一次有效清运） */
    @POST
    @Path("/scan")
    public ScanResultDto scan(ScanRequest req) {
        return bagService.scan(req.bagCode,
                req.collector == null || req.collector.isBlank() ? "清运员" : req.collector);
    }

    /** 双方约定遗失（视同清运计一次费） */
    @POST
    @Path("/lost")
    public BagDto markLost(LostRequest req) {
        return bagService.markLost(req.bagCode, req.month, req.reason);
    }

    /** 破损等情况备注，沿用原袋号，不另建记录 */
    @POST
    @Path("/note")
    public BagDto note(NoteRequest req) {
        return bagService.note(req.bagCode, req.note);
    }

    @GET
    public List<BagDto> list(@QueryParam("ownerCode") String ownerCode) {
        return bagService.listByOwner(ownerCode);
    }
}
