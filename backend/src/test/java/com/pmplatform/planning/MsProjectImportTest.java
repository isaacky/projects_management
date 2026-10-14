package com.pmplatform.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.planning.MsProjectImport.ImportedTask;
import com.pmplatform.planning.MsProjectImport.Parsed;
import com.pmplatform.platform.web.BusinessRuleException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MsProjectImportTest {

    static InputStream sample() {
        return MsProjectImportTest.class.getResourceAsStream("/planning/sample-msproject.xml");
    }

    @Test
    void readsTasksOutlineLinksAndProgress() {
        Parsed p = MsProjectImport.parse(sample());
        assertThat(p.projectStart()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(p.tasks()).hasSize(5); // the project summary row (UID 0) is skipped
        ImportedTask survey = p.tasks().get(1);
        assertThat(survey.name()).isEqualTo("Route survey");
        assertThat(survey.outlineLevel()).isEqualTo(2);
        assertThat(survey.durationDays()).isEqualTo(5);
        assertThat(survey.actualFinish()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(p.tasks().get(0).summary()).isTrue();
        assertThat(p.tasks().get(4).durationDays()).isEqualTo(0); // milestone
        assertThat(p.links()).hasSize(4);
        assertThat(p.links().get(0).type()).isEqualTo(DependencyType.SS);
        assertThat(p.links().get(0).lagDays()).isEqualTo(2);
        assertThat(p.warnings()).hasSize(1); // the finish-to-finish link
    }

    @Test
    void refusesExternalEntitiesAndOtherFiles() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE p [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<Project><Tasks><Task><UID>1</UID><Name>&x;</Name></Task></Tasks></Project>";
        refused(xxe);
        refused("<Workbook><Sheet/></Workbook>");
        refused("not xml at all");
    }

    static void refused(String xml) {
        try {
            MsProjectImport.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (BusinessRuleException expected) {
            return;
        }
        throw new AssertionError("expected refusal for: " + xml);
    }
}
