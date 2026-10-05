package org.example.wastebag.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.example.wastebag.model.Bag;
import org.example.wastebag.service.BagService;

import java.time.LocalDateTime;
import java.util.List;

@Path("/api/bags")
public class BagResource {

    private final BagService bagService;

    public BagResource(BagService bagService) {
        this.bagService = bagService;
    }

    public record IssueIn(Long ownerId, Integer count, String at) {}
    public record AtIn(String at) {}
    public record DamageIn(String note, String at) {}

    @GET
    public List<Bag> list(@QueryParam("ownerId") Long ownerId, @QueryParam("status") String status) {
        if (ownerId != null && status != null) {
            return Bag.list("owner.id = ?1 and status = ?2 order by id",
                    ownerId, org.example.wastebag.model.BagStatus.valueOf(status));
        }
        if (ownerId != null) {
            return Bag.list("owner.id = ?1 order by id", ownerId);
        }
        if (status != null) {
            return Bag.list("status = ?1 order by id", org.example.wastebag.model.BagStatus.valueOf(status));
        }
        return Bag.listAll();
    }

    /** 物业登记发袋：只登记领用，不产生任何费用 */
    @POST
    @Path("/issue")
    public List<BagService.IssuedBag> issue(IssueIn in) {
        return bagService.issue(in.ownerId(), in.count() == null ? 1 : in.count(), parse(in.at()));
    }

    /** 未使用退袋：不产生任何费用 */
    @POST
    @Path("/{bagNo}/return")
    public Bag returnBag(@PathParam("bagNo") String bagNo, AtIn in) {
        return bagService.returnBag(bagNo, in != null ? parse(in.at()) : null);
    }

    /** 报失：按约定赔付，费用随出账计入 */
    @POST
    @Path("/{bagNo}/lost")
    public Bag lost(@PathParam("bagNo") String bagNo, AtIn in) {
        return bagService.markLost(bagNo, in != null ? parse(in.at()) : null);
    }

    /** 破损登记：沿用原袋号，不新建记录、不计费 */
    @POST
    @Path("/{bagNo}/damage")
    public Bag damage(@PathParam("bagNo") String bagNo, DamageIn in) {
        return bagService.markDamaged(bagNo, in != null ? in.note() : null, in != null ? parse(in.at()) : null);
    }

    static LocalDateTime parse(String at) {
        return at == null || at.isBlank() ? null : LocalDateTime.parse(at);
    }
}
