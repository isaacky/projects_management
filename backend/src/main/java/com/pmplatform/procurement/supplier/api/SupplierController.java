package com.pmplatform.procurement.supplier.api;

import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.procurement.supplier.SupplierService;
import com.pmplatform.procurement.supplier.api.SupplierDtos.ImportResult;
import com.pmplatform.procurement.supplier.api.SupplierDtos.SupplierRequest;
import com.pmplatform.procurement.supplier.api.SupplierDtos.SupplierResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    static final String TEMPLATE = "Supplier Name,KRA PIN,Registration No,Email,Phone,Address,Categories,"
            + "AGPO Category,AGPO Certificate No,AGPO Expiry,Status\r\n"
            + "Example Supplies Ltd,P051234567X,CPR/2015/123456,tenders@example.co.ke,+254700000000,"
            + "\"P.O. Box 1, Nairobi\",Transformers; Cables,Youth,AGPO/Y/1234,30/06/2027,Active\r\n";

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public List<SupplierResponse> list() {
        return service.list(TenantContext.require());
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest body) {
        SupplierResponse created = service.create(TenantContext.require(), body);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest body) {
        return service.update(TenantContext.require(), id, body);
    }

    /** {@code apply=false} (default) previews; {@code apply=true} saves. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw new BusinessRuleException("Save the spreadsheet as CSV first (File > Save As > CSV UTF-8), "
                    + "then import the .csv file");
        }
        return service.importCsv(TenantContext.require(), decode(file.getBytes()), apply);
    }

    @GetMapping(path = "/import/template", produces = "text/csv")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"supplier-import-template.csv\"")
                .body(TEMPLATE);
    }

    /** UTF-8 if valid, otherwise Windows-1252 (older Excel "CSV" exports). */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }
}
