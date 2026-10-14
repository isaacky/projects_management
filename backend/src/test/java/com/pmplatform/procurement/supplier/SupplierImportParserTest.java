package com.pmplatform.procurement.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.procurement.supplier.SupplierImportParser.Field;
import com.pmplatform.procurement.supplier.SupplierImportParser.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplierImportParserTest {

    @Test
    void readsQuotedCsvAndSemicolons() {
        assertThat(CsvReader.parse("a,b\r\n\"x, y\",\"he said \"\"hi\"\"\"\n\n1,\"multi\nline\"\n"))
                .containsExactly(List.of("a", "b"), List.of("x, y", "he said \"hi\""), List.of("1", "multi\nline"));
        assertThat(CsvReader.parse("﻿Name;PIN\nAcme;P000000001A"))
                .containsExactly(List.of("Name", "PIN"), List.of("Acme", "P000000001A"));
    }

    @Test
    void matchesCommonHeadersAndValidatesRows() {
        Result r = SupplierImportParser.parse(
                "Supplier Name,KRA PIN,Email Address,Mobile No,AGPO Category,AGPO Expiry,Status,Notes\n"
                        + "Acme Ltd, p 000 000 001 a ,bids@acme.example,0700000001,Youth,30/06/2027,Approved,x\n"
                        + "Bad Pin Ltd,12345,notanemail,,Gold,someday,weird,\n"
                        + ",P000000003C,,,,,,\n"
                        + "Dup Ltd,P000000001A,,,,,,\n");
        assertThat(r.fileErrors()).isEmpty();
        assertThat(r.unmappedColumns()).containsExactly("Notes");
        assertThat(r.rows()).hasSize(4);
        assertThat(r.rows().get(0).valid()).isTrue();
        assertThat(r.rows().get(0).get(Field.KRA_PIN)).isEqualTo("P000000001A");
        assertThat(r.rows().get(0).get(Field.AGPO_CATEGORY)).isEqualTo("YOUTH");
        assertThat(r.rows().get(0).get(Field.AGPO_EXPIRY)).isEqualTo("2027-06-30");
        assertThat(r.rows().get(1).valid()).isFalse();
        assertThat(r.rows().get(1).warnings()).hasSize(4);
        assertThat(r.rows().get(2).errors()).containsExactly("Supplier name is missing");
        assertThat(r.rows().get(3).errors().get(0)).contains("more than once");
    }

    @Test
    void reportsMissingRequiredColumns() {
        assertThat(SupplierImportParser.parse("Company,Phone\nA,1").fileErrors()).hasSize(1);
        assertThat(SupplierImportParser.parse("").fileErrors()).containsExactly("The file is empty");
    }
}
