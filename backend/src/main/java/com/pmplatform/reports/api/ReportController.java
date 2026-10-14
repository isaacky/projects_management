package com.pmplatform.reports.api;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.ReportInfo;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.ReportService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Report catalogue, results as JSON, and Excel downloads. Filters travel as query parameters. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportInfo> catalogue() {
        return service.catalogue(TenantContext.require());
    }

    @GetMapping("/{id}/filters")
    public List<Filter> filters(@PathVariable String id, @RequestParam Map<String, String> params) {
        return service.filters(TenantContext.require(), id, params);
    }

    @GetMapping("/{id}")
    public ReportResult run(@PathVariable String id, @RequestParam Map<String, String> params) {
        return service.run(TenantContext.require(), id, params);
    }

    @GetMapping("/{id}/xlsx")
    public ResponseEntity<byte[]> xlsx(@PathVariable String id, @RequestParam Map<String, String> params) {
        byte[] body = service.xlsx(TenantContext.require(), id, params);
        String name = id + "-" + LocalDate.now(ZoneId.of("Africa/Nairobi")) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
