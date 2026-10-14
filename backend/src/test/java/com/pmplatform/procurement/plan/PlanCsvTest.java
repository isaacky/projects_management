package com.pmplatform.procurement.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.procurement.tender.ReservedGroup;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlanCsvTest {

    @Test
    void readsLooseHeadersAndValues() {
        String csv = "﻿Item description;Category;Qty;UoM;Estimated cost (KES);Procurement method;Quarter;"
                + "Source of funds;Vote code;AGPO;Project code\n"
                + "Transformers;Goods;120;each;\"36,000,000.00\";Open tender;Q2;GoK;D-2110;;NET-2026-001\n"
                + "Bush clearing;Services;300;km;KES 2 400 000;RFQ;2nd;Internal;R-3140;Women;\n"
                + ";Consultancy;1;lot;1.234;Direct;5;;;maybe;\n";
        PlanCsv.Result r = PlanCsv.parse(csv);
        assertThat(r.fileErrors()).isEmpty();
        assertThat(r.rows()).hasSize(3);

        PlanCsv.Row first = r.rows().get(0);
        assertThat(first.errors()).isEmpty();
        assertThat(first.line()).isEqualTo(2);
        assertThat(first.draft().category()).isEqualTo(PlanCategory.GOODS);
        assertThat(first.draft().estimateMinor()).isEqualTo(3_600_000_000L);
        assertThat(first.draft().quantity()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(first.draft().quarter()).isEqualTo(2);
        assertThat(first.draft().method()).isEqualTo("Open tender");
        assertThat(first.draft().projectCode()).isEqualTo("NET-2026-001");
        assertThat(first.draft().reserved()).isNull();

        PlanCsv.Row second = r.rows().get(1);
        assertThat(second.errors()).isEmpty();
        assertThat(second.draft().category()).isEqualTo(PlanCategory.NON_CONSULTING);
        assertThat(second.draft().estimateMinor()).isEqualTo(240_000_000L);
        assertThat(second.draft().reserved()).isEqualTo(ReservedGroup.WOMEN);

        PlanCsv.Row bad = r.rows().get(2);
        assertThat(bad.errors()).hasSize(4); // description, estimate (3 decimals), quarter, reserved
    }

    @Test
    void readsTheDownloadableTemplate() {
        PlanCsv.Result r = PlanCsv.parse("Description,Category,Quantity,Unit,Estimate (KES),Method,Quarter,"
                + "Source of funds,Budget code,Reserved,Project code\r\n"
                + "Supply of office chairs,Goods,40,each,600000,RFQ,Q2,Internal resources,R-2210,Youth,\r\n");
        assertThat(r.fileErrors()).isEmpty();
        assertThat(r.rows().get(0).errors()).isEmpty();
        assertThat(r.rows().get(0).draft().estimateMinor()).isEqualTo(60_000_000L);
        assertThat(r.rows().get(0).draft().reserved()).isEqualTo(ReservedGroup.YOUTH);
    }

    @Test
    void missingColumnsAreReported() {
        PlanCsv.Result r = PlanCsv.parse("Description,Amount\nChairs,1000\n");
        assertThat(r.fileErrors()).containsExactlyInAnyOrder("No category column", "No method column",
                "No quarter column");
        assertThat(PlanCsv.amountMinor("Ksh. 900,000.5")).isEqualTo(90_000_050L);
        assertThat(PlanCsv.amountMinor("-5")).isNull();
        assertThat(PlanCsv.quarter("Quarter 4")).isEqualTo(4);
        assertThat(PlanCsv.category("consulting services")).isEqualTo(PlanCategory.CONSULTANCY);
    }
}
