package org.example.wastebag.resource;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.example.wastebag.service.BagService;

/**
 * 清运员扫码入口：同一袋号重复扫码只登记一次有效清运。
 */
@Path("/api/scan")
public class ScanResource {

    private final BagService bagService;

    public ScanResource(BagService bagService) {
        this.bagService = bagService;
    }

    public record ScanIn(String bagNo, String operator, String at) {}

    @POST
    public BagService.ScanResult scan(ScanIn in) {
        if (in == null || in.bagNo() == null || in.bagNo().isBlank()) {
            throw new WebApplicationException("袋号必填", Response.Status.BAD_REQUEST);
        }
        return bagService.scan(in.bagNo().trim(), in.operator(), BagResource.parse(in.at()));
    }
}
